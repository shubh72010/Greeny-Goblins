/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.MusicAnalysisEntity
import moe.rukamori.archivetune.db.entities.MusicAnalysisSampleEntity

/**
 * Glue for the non-ML backbone: checkpoint → PCM snapshot → DSP → aggregate → Room.
 * Playback never waits: all work leaves on [AnalysisScheduler], PCM is discarded
 * after each analysis, only features persist.
 *
 * YAMNet / GG head / Laya attach here later (STOP line — not this change).
 */
class TrackAnalysisOrchestrator(
    private val scope: CoroutineScope,
    private val player: Player,
    private val database: MusicDatabase,
    /** False when rendering remotely (Cast): tee PCM would be stale, so skip. */
    private val isLocalPlayback: () -> Boolean = { true },
) {
    companion object {
        // v7: real Essentia (rebuilt 2026-09-25 with full dep closure) replaces
        // the dead blob — outputs change across the board, so replay everything.
        const val ANALYSIS_VERSION = 7
        /** Partial finalization needs at least this many windows; one window isn't evidence. */
        const val MIN_PARTIAL_WINDOWS = 2
    }

    private val buffer = PcmRingBuffer()
    private val contextBuffer = LongContextBuffer()
    private var lastMediaId: String? = null
    private val scheduler = AnalysisScheduler(scope)
    private val sampler = CheckpointSampler(scope, player, ::onCheckpoint, ::finalizePartial)
    private val samples = mutableMapOf<String, MutableList<FeatureAggregator.SamplePoint>>()
    private val extrasTaken = mutableMapOf<String, Int>()
    private val cacheChecked = mutableSetOf<String>()
    private val skippedCached = mutableSetOf<String>()
    private var finalizeJobs = mutableMapOf<String, Job>()

    fun attach() = sampler.attach()

    fun release() {
        sampler.release()
        finalizeJobs.values.forEach { it.cancel() }
        finalizeJobs.clear()
        runCatching {
            buffer.reset()
            contextBuffer.reset()
        }
    }

    /** Called from the existing ExoPlayer tee fan-out (background handler, never audio thread). */
    fun onPcm(hop: ShortArray, sampleRate: Int) {
        runCatching {
            buffer.write(hop, sampleRate)
            contextBuffer.feed(hop, sampleRate)
        }
    }

    private fun onCheckpoint(mediaId: String, fraction: Double, positionMs: Long, durationMs: Long) {
        if (!runCatching { isLocalPlayback() }.getOrDefault(true)) return
        if (lastMediaId != mediaId) {
            lastMediaId = mediaId
            buffer.reset()
            contextBuffer.reset()
        }
        synchronized(samples) {
            if (mediaId in skippedCached) return
        }
        val snapshot = buffer.snapshot() ?: return
        scheduler.schedule {
            if (isCached(mediaId)) return@schedule
            val mono16k = DspAnalyzer.resampleLinearMono(snapshot.samples, snapshot.sampleRate)
            // Need a full window; trailing buffer underfills only at track start.
            if (mono16k.size < DspAnalyzer.TARGET_RATE * 2) return@schedule
            val sample = DspAnalyzer.analyze(mono16k)
            val point = FeatureAggregator.SamplePoint(fraction, sample)
            val all = synchronized(samples) {
                val list = samples.getOrPut(mediaId) { mutableListOf() }
                list.add(point)
                list.toList()
            }
            // Persist the raw per-window candidates immediately: if the aggregate
            // is ever wrong-but-confident, the winners that produced it survive.
            runCatching {
                if (all.size == 1) database.clearMusicAnalysisSamples(mediaId)
                database.upsertMusicAnalysisSample(
                    MusicAnalysisSampleEntity(
                        videoId = mediaId,
                        fraction = fraction,
                        bpm = sample.bpm,
                        chromaKey = sample.key,
                        analyzedAt = System.currentTimeMillis(),
                    ),
                )
            }
            maybeFinalize(mediaId, all)
        }
    }

    /** Room lookup once per track: already analyzed → skip the whole pipeline. */
    private suspend fun isCached(mediaId: String): Boolean {
        synchronized(samples) {
            if (mediaId in skippedCached) return true
            if (mediaId in cacheChecked) return false
            cacheChecked.add(mediaId)
        }
        val existing = runCatching { database.getMusicAnalysis(mediaId) }.getOrNull()
        if (existing != null && existing.analysisVersion >= ANALYSIS_VERSION) {
            synchronized(samples) { skippedCached.add(mediaId) }
            return true
        }
        return false
    }

    private fun maybeFinalize(mediaId: String, all: List<FeatureAggregator.SamplePoint>) {
        val baseDone = all.count { it.fraction in CheckpointMath.BASE_FRACTIONS } >= CheckpointMath.BASE_FRACTIONS.size
        if (!baseDone) return
        val aggregated = FeatureAggregator.aggregate(all) ?: return
        val taken = synchronized(samples) { extrasTaken[mediaId] ?: 0 }
        if (FeatureAggregator.needsExtraSamples(aggregated, taken)) {
            val next = sampler.requestExtraSample()
            if (next != null) {
                synchronized(samples) { extrasTaken[mediaId] = taken + 1 }
            }
            // Fall through: persist current best now; the extra refines it if reached.
        }
        // Long-context tempo/key over up to 120s (Essentia → JVM fallback);
        // falls back to window means when the track hasn't accumulated enough
        // context (skips, short tracks). Still on the scheduler lane — the
        // ~200ms pass never touches playback.
        val long = DspAnalyzer.analyzeTempoKey(contextBuffer.snapshot())
        persist(mediaId, aggregated, all, long)
    }

    /**
     * Track ended (advance/skip/natural end) before the 80% path finalized.
     * Persists whatever windows exist (minimum [MIN_PARTIAL_WINDOWS]) with the
     * long-context tempo/key over the partial envelope — played 37% still yields
     * an estimate instead of wasted work. Called on the main thread; the heavy
     * pass leaves on the scheduler lane with a synchronously-captured snapshot.
     */
    private fun finalizePartial(mediaId: String?) {
        if (mediaId == null) return
        val snap = synchronized(samples) { samples[mediaId]?.toList() } ?: return
        if (snap.size < MIN_PARTIAL_WINDOWS) return
        val context = contextBuffer.snapshot()
        scheduler.schedule {
            val aggregated = FeatureAggregator.aggregate(snap) ?: return@schedule
            val long = DspAnalyzer.analyzeTempoKey(context)
            persist(mediaId, aggregated, snap, long)
        }
    }

    private fun persist(
        mediaId: String,
        aggregated: FeatureAggregator.AggregatedResult,
        all: List<FeatureAggregator.SamplePoint>,
        long: DspAnalyzer.LongContextResult,
    ) {
        finalizeJobs[mediaId]?.cancel()
        finalizeJobs[mediaId] = scope.launch {
            val existing = runCatching { database.getMusicAnalysis(mediaId) }.getOrNull()
            if (existing != null && existing.analysisVersion >= ANALYSIS_VERSION) return@launch
            val entity = MusicAnalysisEntity(
                videoId = mediaId,
                rms = aggregated.rms,
                bass = aggregated.bass,
                mids = aggregated.mids,
                treble = aggregated.treble,
                brightness = aggregated.brightness,
                spectralFlux = aggregated.spectralFlux,
                onsetDensity = aggregated.onsetDensity,
                // Windows never report tempo (3s path is spectral-only), so take
                // rhythmicity from whichever long-context pass delivered the BPM.
                rhythmicity = if (long.bpm > 0) long.rhythmicity else aggregated.rhythmicity,
                bpm = long.bpm.takeIf { it > 0 },
                chromaKey = long.key,
                chromaScale = long.scale,
                confidence = aggregated.overallConfidence,
                sampleCount = all.size,
                analysisVersion = ANALYSIS_VERSION,
                analyzedAt = System.currentTimeMillis(),
                analysisSource = long.analysisSource,
                analysisError = long.error,
            )
            runCatching { database.upsertMusicAnalysis(entity) }
            synchronized(samples) {
                samples.remove(mediaId)
                extrasTaken.remove(mediaId)
            }
        }
    }
}

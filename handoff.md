# Handoff: Essentia Native Library Integration

## Current State

**Status:** JNI name fixed + KeyExtractor rebuilt + libraries updated + app reinstalled on device (PID 29732, package `moe.rukamori.archivetune.debug`)

## Root Cause Found
The original crash was a **JNI name mismatch** — the C++ function was named `nativeAnalyze` but Kotlin declared it as `analyze`.

**Error from logcat:**
```
No implementation found for java.lang.String moe.rukamori.archivetune.audio.essentia.EssentiaNative.analyze(float[], int)
```

## Fixes Applied
1. **`/tmp/kilo/essentia/jni/essentia_jni.cpp:15`** — Renamed JNI function from `Java_moe_rukamori_archivetune_audio_essentia_EssentiaNative_nativeAnalyze` to `Java_moe_rukamori_archivetune_audio_essentia_EssentiaNative_analyze`
2. **Rebuilt Essentia** with `--include-algos=KeyExtractor,BeatTrackerDegara` (previously only BeatTrackerDegara was compiled)
3. **Copied updated libraries** to `app/src/main/jniLibs/arm64-v8a/`:
   - `libessentia.so` (1.4 MB) — rebuilt with KeyExtractor
   - `libessentia_jni.so` (30 KB) — rebuilt with correct JNI function name
4. **Rebuilt app** (`assembleGmsMobileUniversalDebug`) and reinstalled on device

## What Needs Testing on Device
1. Play a track for 1+ minutes (need to cross 20%/50%/80% checkpoints for analysis to trigger)
2. Analysis runs at `TrackAnalysisOrchestrator.kt:141` → `DspAnalyzer.analyzeEssentiaLongContext()` → `EssentiaNative.analyzeTrack()` → JNI `analyze()`
3. Look for `EssentiaJNI` tag in logcat (C++ logs) and check `MusicIntelligenceScreen` for displayed BPM/key
4. Check Room DB `MusicAnalysisEntity` for `analysisSource="essentia"` with populated `bpm`/`chromaKey` fields

## License Check Pending
Essentia AGPL-3.0 vs project GPL-3.0 — verify if dynamic linking of the `.so` is acceptable.

## Relevant Files
- `app/src/main/kotlin/moe/rukamori/archivetune/audio/essentia/EssentiaNative.kt:23` — Kotlin JNI declaration
- `app/src/main/kotlin/moe/rukamori/archivetune/audio/analysis/DspAnalyzer.kt:119` — `analyzeEssentiaLongContext`
- `app/src/main/kotlin/moe/rukamori/archivetune/audio/analysis/TrackAnalysisOrchestrator.kt:141` — calls Essentia analysis
- `/tmp/kilo/essentia/jni/essentia_jni.cpp` — JNI wrapper source
- `app/src/main/jniLibs/arm64-v8a/` — deployed native libraries

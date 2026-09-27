/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Canonical Essentia JNI wrapper source. The prebuilt
 * app/src/main/jniLibs/<abi>/libessentia_jni.so was compiled from THIS file
 * (previously it lived only in /tmp/kilo and was lost — never again).
 *
 * JNI name MUST stay
 *   Java_moe_rukamori_archivetune_audio_essentia_EssentiaNative_nativeAnalyze
 * because EssentiaNative.kt declares `private external fun nativeAnalyze`.
 * Rename both together or not at all.
 *
 * Rebuild (per ABI, needs Essentia Android headers + libessentia.so):
 *   1. Unpack Essentia Android prebuilts to
 *      app/src/main/cpp/third_party/essentia/{include,lib/<abi>}.
 *   2. The conditional externalNativeBuild block in app/build.gradle.kts
 *      activates automatically; then DELETE the matching
 *      app/src/main/jniLibs/<abi>/libessentia_jni.so blob so the fresh
 *      build is the single source of that .so (else AGP duplicate-file error).
 *   3. ABIs without a prebuilt compile the stub (essentia_jni_stub.cpp),
 *      which returns an error token — Kotlin falls back to the JVM DSP.
 *
 * Essentia is AGPL-3.0, shipped as a dynamically-linked prebuilt.
 * GPLv3 §13 permits combining GPLv3 project code with AGPLv3 Essentia; the
 * distributed combination is then subject to AGPLv3's additional terms
 * (notably the §13 Corresponding Source offer). For this locally-run app
 * that cashes out as a release checklist, not a relicense: ship the AGPLv3
 * text, Corresponding Source for the exact libessentia build (version +
 * source location, see ESSENTIA_PROVENANCE.md), preserved copyright
 * notices, and an Essentia entry in the OSS-licenses screen.
 */

#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

#include <essentia/algorithmfactory.h>
#include <essentia/essentia.h>

#define LOG_TAG "EssentiaJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// BeatTrackerDegara hardcodes sampleRate=44100 internally (see its configure()),
// so input is always resampled here. KeyExtractor is rate-agnostic but gets the
// same signal for one pass.
static constexpr int kEssentiaRate = 44100;

static std::vector<float> resampleLinear(const float* in, size_t n, int fromRate) {
    if (fromRate == kEssentiaRate || n == 0) return std::vector<float>(in, in + n);
    const double ratio = static_cast<double>(fromRate) / kEssentiaRate;
    const size_t outSize = std::max<size_t>(1, static_cast<size_t>(n / ratio));
    std::vector<float> out(outSize);
    for (size_t i = 0; i < outSize; ++i) {
        const double pos = i * ratio;
        const size_t idx = std::min<size_t>(static_cast<size_t>(pos), n - 1);
        const double frac = pos - idx;
        const float a = in[idx];
        const float b = in[std::min(idx + 1, n - 1)];
        out[i] = static_cast<float>(a + (b - a) * frac);
    }
    return out;
}

// Essentia 2.1-beta5's streaming BeatTrackerDegara emits ONLY "ticks" (beat
// positions in seconds) — there is no "bpm" output, and asking for one throws
// "Couldn't find 'bpm' in BeatTrackerDegara::outputs". Tempo is therefore the
// reciprocal of the tick period. Median (not mean) of the intervals: one
// skipped or doubled beat must not drag the estimate, while the median stays
// stable across syncopation.
static float bpmFromTicks(const std::vector<float>& ticks) {
    if (ticks.size() < 3) return 0.0f;
    std::vector<float> intervals;
    intervals.reserve(ticks.size() - 1);
    for (size_t i = 1; i < ticks.size(); ++i) {
        const float d = ticks[i] - ticks[i - 1];
        if (d > 1e-3f) intervals.push_back(d);
    }
    if (intervals.size() < 2) return 0.0f;
    const size_t mid = intervals.size() / 2;
    std::nth_element(intervals.begin(), intervals.begin() + mid, intervals.end());
    const float period = intervals[mid];
    if (period <= 1e-3f) return 0.0f;
    float bpm = 60.0f / period;
    while (bpm < 60.0f) bpm *= 2.0f;
    while (bpm > 200.0f) bpm /= 2.0f;
    return bpm;
}

extern "C" JNIEXPORT jstring JNICALL
Java_moe_rukamori_archivetune_audio_essentia_EssentiaNative_nativeAnalyze(
        JNIEnv* env, jobject /*thiz*/, jfloatArray samples, jint sampleRate) {
    if (samples == nullptr) return env->NewStringUTF("0|||empty input");
    const jsize n = env->GetArrayLength(samples);
    if (n <= 0) return env->NewStringUTF("0|||empty input");

    std::vector<float> mono(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, mono.data());
    std::vector<float> audio = resampleLinear(mono.data(), mono.size(), sampleRate);

    try {
        essentia::init();

        essentia::standard::Algorithm* beatTracker =
                essentia::standard::AlgorithmFactory::create("BeatTrackerDegara");
        std::vector<float> ticks;
        beatTracker->input("signal").set(audio);
        beatTracker->output("ticks").set(ticks);
        beatTracker->compute();
        delete beatTracker;
        const float bpm = bpmFromTicks(ticks);

        // Key extraction is best-effort: a key failure must never discard a
        // valid tempo (the previous exception ordering threw away a good BPM
        // because the key ran second). Tempo is the primary result.
        std::string key, scale;
        try {
            essentia::standard::Algorithm* keyExtractor =
                    essentia::standard::AlgorithmFactory::create("KeyExtractor");
            // "strength" is Output<Real> in 2.1-beta5 (declaring it as
            // std::string throws a type check error).
            essentia::Real strength = 0.0;
            keyExtractor->input("audio").set(audio);
            keyExtractor->output("key").set(key);
            keyExtractor->output("scale").set(scale);
            keyExtractor->output("strength").set(strength);
            keyExtractor->compute();
            delete keyExtractor;
        } catch (const std::exception& ke) {
            LOGE("key extraction failed: %s", ke.what());
            key.clear();
            scale.clear();
        }

        essentia::shutdown();

        const int bpmInt = static_cast<int>(std::round(bpm));
        if (bpmInt <= 0) return env->NewStringUTF("0|||no beat detected");
        char out[64];
        snprintf(out, sizeof(out), "%d|%s|%s", bpmInt, key.c_str(), scale.c_str());
        return env->NewStringUTF(out);
    } catch (const std::exception& e) {
        LOGE("analyze failed: %s", e.what());
        essentia::shutdown();
        std::string err = std::string("0|||") + e.what();
        return env->NewStringUTF(err.c_str());
    }
}

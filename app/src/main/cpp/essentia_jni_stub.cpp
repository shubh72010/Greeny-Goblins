/*
 * JusPlayer (2026) — Essentia JNI stub.
 * Compiled for ABIs with no libessentia.so prebuilt (see CMakeLists.txt).
 * Returns the error-token format EssentiaNative.parseResult treats as
 * "native present but analysis unavailable" → Kotlin falls back to JVM DSP.
 * The JNI symbol name MUST match the real wrapper (essentia_jni.cpp).
 */

#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_moe_rukamori_archivetune_audio_essentia_EssentiaNative_nativeAnalyze(
        JNIEnv* env, jobject /*thiz*/, jfloatArray /*samples*/, jint /*sampleRate*/) {
    return env->NewStringUTF("0|||essentia not bundled for this ABI");
}

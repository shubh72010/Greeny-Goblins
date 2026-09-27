# JusPlayer — Essentia JNI rebuild.
# Active only when app/src/main/cpp/third_party/essentia/ holds Essentia
# Android prebuilts (include/ + lib/<abi>/libessentia.so); otherwise the
# externalNativeBuild block in app/build.gradle.kts stays dormant and the
# jniLibs blobs are used. ABIs without a prebuilt compile the stub, which
# returns an error token so Kotlin falls back to the JVM DSP — every ABI
# always produces a libessentia_jni.so, never a missing-library crash.
cmake_minimum_required(VERSION 3.22)
project(essentia_jni)

set(ESSENTIA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/essentia")
set(ESSENTIA_PREBUILT "${ESSENTIA_DIR}/lib/${ANDROID_ABI}/libessentia.so")

find_library(log-lib log)

if(EXISTS "${ESSENTIA_DIR}/include/essentia/essentia.h" AND EXISTS "${ESSENTIA_PREBUILT}")
  add_library(essentia SHARED IMPORTED)
  set_target_properties(essentia PROPERTIES IMPORTED_LOCATION "${ESSENTIA_PREBUILT}")
  add_library(essentia_jni SHARED essentia_jni.cpp)
  target_include_directories(essentia_jni PRIVATE "${ESSENTIA_DIR}/include")
  target_link_libraries(essentia_jni essentia ${log-lib})
else()
  add_library(essentia_jni SHARED essentia_jni_stub.cpp)
  target_link_libraries(essentia_jni ${log-lib})
endif()

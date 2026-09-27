# Essentia native provenance

## Current blob (inherited, NOT reproducible)

- Location: `app/src/main/jniLibs/arm64-v8a/libessentia.so` (+ `libessentia_jni.so`, `libc++_shared.so`)
- Version: **Essentia 2.1-beta5** (from `strings`: `essentia::version` = `2.1-beta5`)
- Algorithms compiled in: **exactly two** — `BeatTrackerDegara`, `KeyExtractor`
  (only these two `AlgorithmFactory::Registrar` symbols exist in the `.so`)
- Toolchain: `Android clang 19.0.1` (NDK r27-era)
- ABI: **arm64-v8a only** — all other ABIs fall back to the JVM DSP
- Origin: built by a previous agent in `/tmp/kilo`; that source tree is gone,
  so the exact Essentia commit, waf flags, and dependency versions are unknown

Status: version identified, Corresponding Source NOT established.
Do not ship a store release on this blob alone.

## Proven broken (2026-09-25, logcat on arm64 device)

`BeatTrackerDegara could not be configured ... Identifier 'FrameCutter' not
found in registry... Available algorithms: BeatTrackerDegara KeyExtractor`

The lightweight build stripped BeatTrackerDegara's transitive dependencies
(FrameCutter and friends), so it can never instantiate — Essentia tempo is
dead by construction with this blob, not by wiring. KeyExtractor never gets
attempted (the wrapper creates the beat tracker first). Any rebuild MUST
include the full dependency closure of both algorithms; a bare
`--include-algos=BeatTrackerDegara,KeyExtractor` reproduces this exact failure.

## Rebuild 2026-09-25 (arm64-v8a) — WORKING

- Source: `MTG/essentia` tag `v2.1_beta5`, commit `ed59cc48e37ac33ac15b252fc5ad7af4b9ecd51d`
- Toolchain: NDK `28.2.13676358`, clang 19.0.1, `aarch64-linux-android26`
  (minSdk 26), `python3.10` (bundled waf 2.0.10 breaks on 3.11+)
- waf flags: `configure --cross-compile-android --lightweight= --fft=KISS
  --build-static --mode=release --include-algos=<13 below>`
- Algorithm closure (flat `--include-algos` pulls NO transitive deps — the
  previous failure): `BeatTrackerDegara KeyExtractor FrameCutter Windowing
  FFT IFFT CartesianToPolar OnsetDetection TempoTapDegara Spectrum
  SpectralPeaks PeakDetection SpectralWhitening HPCP Key Magnitude NoiseAdder
  AutoCorrelation MovingAverage IIR Flux HFC MelBands TriangularBands`
  (FFT/IFFT resolve to FFTK/IFFTK via KISS; closure computed by scanning
  `factory.create()` in every included .h AND .cpp — deps hide in headers
  too, e.g. FrameCutter creates NoiseAdder from framecutter.h)
- `libessentia_jni.so` (7.2 MB): `app/src/main/cpp/essentia_jni.cpp` linked
  against `build/src/libessentia.a`, `-static-libstdc++`, 16KB page size
  (`-Wl,-z,max-page-size=16384`). NEEDED: only liblog/libm/libdl/libc —
  the old `libessentia.so` + `libc++_shared.so` blobs are deleted.
- Corresponding Source = upstream tag above + this file + `essentia_jni.cpp`.
- TODO: repeat for `armeabi-v7a x86 x86_64` (same recipe, retarget toolchain
  wrappers); TODO: OSS-licenses screen entry + AGPLv3 text at release.

## Old rebuild recipe (superseded by the above, kept for other ABIs)

1. Clone `https://github.com/MTG/essentia.git`, check out the tag/commit
    matching 2.1-beta5 (or the newest release — then re-validate BPM/key
    output against the app's expectations).
2. Install build deps: Eigen3 (required), NDK r27+ toolchain.
3. Lightweight waf configure per ABI, e.g.:
    `python3 waf configure --mode=android --lightweight=BeatTrackerDegara,KeyExtractor`
    (exact flag spelling per the checked-out Essentia version's `waf --help`).
4. Build `libessentia.so` for `arm64-v8a armeabi-v7a x86 x86_64`,
    place under `app/src/main/cpp/third_party/essentia/{include,lib/<abi>}`.
5. The conditional `externalNativeBuild` in `app/build.gradle.kts` activates;
    delete the matching `jniLibs/<abi>` blobs to avoid AGP duplicate-file errors.
6. Record here: Essentia commit SHA, waf flags, NDK version, dependency
    versions — that record + upstream source IS the Corresponding Source.
7. Add the Essentia entry (version + license + source URL) to the app's
    OSS-licenses screen; ship the AGPLv3 license text.

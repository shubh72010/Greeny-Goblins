#!/usr/bin/env bash
# Rebuilds libessentia_jni.so from pinned upstream source.
#
# Why a script and not a committed binary: .gitignore excludes *.so, so the
# Essentia binary is a build artifact. This script + ESSENTIA_PROVENANCE.md are
# the reproducible path; the app falls back to the JVM DSP when the .so is
# absent (see EssentiaNative.isAvailable).
#
# Usage:  app/src/main/cpp/build-essentia.sh [arm64-v8a|armeabi-v7a|x86|x86_64]
# Requires: NDK at $ANDROID_NDK_HOME (or ~/Android/Sdk/ndk/<version>), git, python3.10
#           (Essentia's bundled waf 2.0.10 breaks on Python 3.11+: `import imp`,
#           and the 'rU' file mode was removed in 3.11).
set -euo pipefail

ABI="${1:-arm64-v8a}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${ESSENTIA_WORK_DIR:-/tmp/essentia-build}"
ESSENTIA_TAG="v2.1_beta5"
ESSENTIA_COMMIT="ed59cc48e37ac33ac15b252fc5ad7af4b9ecd51d"

case "$ABI" in
  arm64-v8a)   TRIPLE="aarch64-linux-android26" ;;
  armeabi-v7a) TRIPLE="armv7a-linux-androideabi26" ;;
  x86)         TRIPLE="i686-linux-android26" ;;
  x86_64)      TRIPLE="x86_64-linux-android26" ;;
  *) echo "unsupported ABI: $ABI" >&2; exit 1 ;;
esac

NDK="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/28.2.13676358}"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$BIN/$TRIPLE-clang++" ] || { echo "missing NDK toolchain: $BIN/$TRIPLE-clang++" >&2; exit 1; }

# Full algorithm closure for BeatTrackerDegara + KeyExtractor. --include-algos
# is a FLAT list: it does not pull transitive dependencies, and several hide in
# headers (FrameCutter creates NoiseAdder from framecutter.h). Omitting any of
# these reproduces "Identifier 'X' not found in registry" at runtime.
ALGOS="BeatTrackerDegara,KeyExtractor,FrameCutter,Windowing,FFT,IFFT,CartesianToPolar,OnsetDetection,TempoTapDegara,Spectrum,SpectralPeaks,PeakDetection,SpectralWhitening,HPCP,Key,Magnitude,NoiseAdder,AutoCorrelation,MovingAverage,IIR,Flux,HFC,MelBands,TriangularBands"

if [ ! -d "$WORK/.git" ]; then
  git clone --depth 1 --branch "$ESSENTIA_TAG" https://github.com/MTG/essentia.git "$WORK"
fi
cd "$WORK"
[ "$(git rev-parse HEAD)" = "$ESSENTIA_COMMIT" ] || { echo "unexpected Essentia checkout" >&2; exit 1; }

# waf's cross path wants bare clang/clang++/ar on PATH; point them at the NDK.
mkdir -p "$WORK/tc/bin"
for tool in clang clang++; do
  printf '#!/bin/sh\nexec %s/%s "$@"\n' "$BIN" "$TRIPLE-$tool" > "$WORK/tc/bin/$tool"
  chmod +x "$WORK/tc/bin/$tool"
done
printf '#!/bin/sh\nexec %s/llvm-ar "$@"\n' "$BIN" > "$WORK/tc/bin/ar"
chmod +x "$WORK/tc/bin/ar"
export PATH="$WORK/tc/bin:$PATH"

python3.10 ./waf distclean >/dev/null 2>&1 || true
python3.10 ./waf configure --cross-compile-android --lightweight= --fft=KISS \
  --build-static --mode=release --include-algos="$ALGOS"
python3.10 ./waf -j"$(nproc)"

OUT="$HERE/../jniLibs/$ABI"
mkdir -p "$OUT"
"$BIN/$TRIPLE-clang++" -O2 -fPIC -std=c++11 -I"$WORK/src" \
  "$HERE/essentia_jni.cpp" "$WORK/build/src/libessentia.a" \
  -llog -latomic -lm -static-libstdc++ -Wl,-z,max-page-size=16384 -shared \
  -o "$OUT/libessentia_jni.so"

echo "built $OUT/libessentia_jni.so"
echo "record the Essentia commit + flags in $HERE/ESSENTIA_PROVENANCE.md"

#!/usr/bin/env bash
# 用 Android NDK 从源码交叉编译 iperf3，输出到 app/src/main/jniLibs/<abi>/libiperf3.so
# 通常只需要在 CI 跑一次，把生成的 .so 提交进仓库即可。
set -euo pipefail

IPERF3_VERSION="${IPERF3_VERSION:-3.17.1}"
API="${ANDROID_API_LEVEL:-24}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  echo "Error: ANDROID_NDK_HOME / ANDROID_NDK_ROOT not set or invalid"
  exit 1
fi

HOST_TAG="linux-x86_64"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
[[ -d "$TOOLCHAIN" ]] || { echo "Toolchain missing: $TOOLCHAIN"; exit 1; }

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/.iperf3-build"
OUT="$ROOT/app/src/main/jniLibs"
mkdir -p "$BUILD" "$OUT"/{arm64-v8a,armeabi-v7a,x86_64,x86}

cd "$BUILD"
SRC="iperf-$IPERF3_VERSION"
if [[ ! -d "$SRC" ]]; then
  curl -fL --retry 3 -o iperf.tgz \
    "https://github.com/esnet/iperf/releases/download/$IPERF3_VERSION/iperf-$IPERF3_VERSION.tar.gz"
  tar xzf iperf.tgz
fi

build_one() {
  local ABI="$1" TRIPLE="$2" CLANG_PREFIX="$3"
  echo "==================== $ABI ===================="
  cd "$BUILD/$SRC"
  make distclean 2>/dev/null || true

  export CC="$TOOLCHAIN/bin/${CLANG_PREFIX}${API}-clang"
  export AR="$TOOLCHAIN/bin/llvm-ar"
  export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
  export STRIP="$TOOLCHAIN/bin/llvm-strip"
  export CFLAGS="-fPIE -O2 -D_FILE_OFFSET_BITS=64"
  export LDFLAGS="-fPIE -pie"

  ac_cv_func_malloc_0_nonnull=yes \
  ac_cv_func_realloc_0_nonnull=yes \
  ./configure --host="$TRIPLE" --without-openssl --disable-shared

  make -j"$(nproc)"
  "$STRIP" src/iperf3
  cp src/iperf3 "$OUT/$ABI/libiperf3.so"
  echo ">>> $ABI done: $(ls -la "$OUT/$ABI/libiperf3.so" | awk '{print $5}') bytes"
}

build_one arm64-v8a   aarch64-linux-android    aarch64-linux-android
build_one armeabi-v7a arm-linux-androideabi    armv7a-linux-androideabi
build_one x86_64      x86_64-linux-android     x86_64-linux-android
build_one x86         i686-linux-android       i686-linux-android

echo
echo "All ABIs built. iperf3 v$IPERF3_VERSION installed to $OUT/<abi>/libiperf3.so"

#!/bin/bash
# build-android.sh - Build mruby 3.4.0 for Android targets
#
# Prerequisites:
#   - Ruby with rake installed
#   - Android NDK r29 installed
#   - mruby 3.4.0 source extracted as a git submodule at mruby/
#
# Usage:
#   ./build-android.sh /path/to/ndk
#

set -euo pipefail

NDK_HOME="${1:-${ANDROID_NDK_HOME:-}}"

if [ -z "$NDK_HOME" ]; then
  echo "Usage: $0 /path/to/android-ndk-r29"
  echo "   or: ANDROID_NDK_HOME=/path/to/ndk $0"
  exit 1
fi

if [ ! -d "$NDK_HOME" ]; then
  echo "ERROR: NDK not found at: $NDK_HOME"
  exit 1
fi

export ANDROID_NDK_HOME="$NDK_HOME"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEST_DIR="${SCRIPT_DIR}/app/src/main/cpp/mruby"

echo "=== Building mruby for Android ==="
echo "NDK: $NDK_HOME"
echo ""

# Build with our custom config (defines Android cross-builds)
echo "--- Building mruby for Android ---"
cd "${SCRIPT_DIR}/mruby"
MRUBY_CONFIG="${SCRIPT_DIR}/build_config.rb" rake

echo ""
echo "--- Copying headers ---"
mkdir -p "${DEST_DIR}/include"
rsync -a --delete "${SCRIPT_DIR}/mruby/include/" "${DEST_DIR}/include/"
rsync -a --delete "${SCRIPT_DIR}/mruby/build/host/include/" "${DEST_DIR}/include/"
echo ""

# Copy libraries
echo "--- Copying libraries ---"
mkdir -p "${DEST_DIR}/lib/arm64-v8a"
mkdir -p "${DEST_DIR}/lib/x86_64"

cp "${SCRIPT_DIR}/mruby/build/android-arm64-v8a/lib/libmruby.a" \
   "${DEST_DIR}/lib/arm64-v8a/libmruby.a"
echo "  arm64-v8a: $(ls -lh "${DEST_DIR}/lib/arm64-v8a/libmruby.a" | awk '{print $5}')"

cp "${SCRIPT_DIR}/mruby/build/android-x86_64/lib/libmruby.a" \
   "${DEST_DIR}/lib/x86_64/libmruby.a"
echo "  x86_64:   $(ls -lh "${DEST_DIR}/lib/x86_64/libmruby.a" | awk '{print $5}')"

echo ""
echo "=== Build complete ==="
echo "Headers: ${DEST_DIR}/include/"
echo "Libraries: ${DEST_DIR}/lib/{arm64-v8a,x86_64}/"
echo ""
echo "To precompile Ruby files to .mrb bytecode:"
echo "  ${SCRIPT_DIR}/mruby/build/host/bin/mrbc -o hello.mrb hello.rb"

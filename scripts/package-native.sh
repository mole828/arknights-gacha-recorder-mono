#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-linuxX64}"
VARIANT="${2:-release}"
case "$TARGET" in
  linuxX64) MODULE=linux; TASK_PLATFORM=LinuxX64 ;;
  macosArm64) MODULE=macos; TASK_PLATFORM=MacosArm64 ;;
  *) echo "Unsupported target: $TARGET (use linuxX64 or macosArm64)" >&2; exit 1 ;;
esac
case "$VARIANT" in
  debug) TASK_VARIANT=Debug ;;
  release) TASK_VARIANT=Release ;;
  *) echo "Unsupported variant: $VARIANT (use debug or release)" >&2; exit 1 ;;
esac

cd "$ROOT_DIR"
./kotlin build -m "$MODULE" -p "$TARGET" -v "$VARIANT"
OUTPUT_DIR="$ROOT_DIR/build/tasks/_${MODULE}_link${TASK_PLATFORM}${TASK_VARIANT}"
BINARY="$(find "$OUTPUT_DIR" -maxdepth 1 -type f -name '*.kexe' -print)"
if [[ -z "$BINARY" || "$BINARY" == *$'\n'* ]]; then
  echo "Expected one executable in $OUTPUT_DIR" >&2
  exit 1
fi
mkdir -p "$ROOT_DIR/dist/$TARGET"
cp "$BINARY" "$ROOT_DIR/dist/$TARGET/arknights-gacha-recorder.kexe"
echo "Packaged dist/$TARGET/arknights-gacha-recorder.kexe"

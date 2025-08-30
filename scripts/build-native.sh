#!/bin/bash
set -e

SRC_ROOT="src/main/native/c"
OUT_ROOT="target/native"

OS=$(uname | tr '[:upper:]' '[:lower:]')
EXT="so"
PREFIX="lib"

if [[ "$OS" == *darwin* ]]; then
  EXT="dylib"
fi

echo "Detected Unix system: $OS"

for DIR in "$SRC_ROOT"/*; do
  [ -d "$DIR" ] || continue
  NAME=$(basename "$DIR")
  SOURCES=$(find "$DIR" -name "*.c")
  OUT_DIR="$OUT_ROOT/$NAME"
  OUT_FILE="$OUT_DIR/${PREFIX}${NAME}.${EXT}"

  mkdir -p "$OUT_DIR"
  echo "Building $NAME → $OUT_FILE"
  gcc -shared -fPIC -O2 -std=c11 $SOURCES -o "$OUT_FILE"
done

echo "Native build finished"
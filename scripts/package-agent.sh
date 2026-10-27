#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
./kotlin package -m agent -f executable-jar
OUTPUT_DIR="$ROOT_DIR/build/tasks/_agent_executableJarJvm"
JAR="$(find "$OUTPUT_DIR" -maxdepth 1 -type f -name '*.jar' -print)"
if [[ -z "$JAR" || "$JAR" == *$'\n'* ]]; then
  echo "Expected one executable JAR in $OUTPUT_DIR" >&2
  exit 1
fi
mkdir -p "$ROOT_DIR/dist/agent"
cp "$JAR" "$ROOT_DIR/dist/agent/agent.jar"

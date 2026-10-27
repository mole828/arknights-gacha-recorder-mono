#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_TARGET="${1:-mt}"
REMOTE_DIR="${REMOTE_DIR:-/home/ubuntu/arknights-gacha-recorder}"
BINARY="$ROOT_DIR/build/bin/linuxX64/releaseExecutable/arknights-gacha-recorder.kexe"

cd "$ROOT_DIR"

./gradlew linkReleaseExecutableLinuxX64 --no-daemon

ssh "$SSH_TARGET" "mkdir -p '$REMOTE_DIR'"
rsync -av --chmod=Fu=rwx,Fgo=rx \
  "$BINARY" \
  "$ROOT_DIR/Dockerfile.runtime" \
  "$ROOT_DIR/docker-compose.native.yml" \
  "$SSH_TARGET:$REMOTE_DIR/"

ssh "$SSH_TARGET" "cd '$REMOTE_DIR' && \
  if docker compose version >/dev/null 2>&1; then compose_cmd='docker compose'; else compose_cmd='docker-compose'; fi && \
  \$compose_cmd -f docker-compose.native.yml up -d --build --remove-orphans && \
  docker ps --filter name=arknights-gacha-recorder --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'"

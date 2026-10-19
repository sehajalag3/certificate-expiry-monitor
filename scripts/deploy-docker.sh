#!/usr/bin/env bash
# Builds the image and starts the app with Docker Compose (Linux/macOS).
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${PORT:-8080}"

docker compose config -q
docker compose up -d --build

echo "Waiting for http://localhost:${PORT}/actuator/health ..."
for i in $(seq 1 45); do
  if curl -fsS "http://localhost:${PORT}/actuator/health" >/dev/null 2>&1; then
    echo "Application is up."
    exit 0
  fi
  sleep 2
done

echo "Application did not become healthy in time. Check: docker compose logs certificate-monitor" >&2
exit 1

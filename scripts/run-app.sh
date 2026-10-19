#!/usr/bin/env bash
# Runs the Certificate Expiry Monitor locally (Linux/macOS).
set -euo pipefail
cd "$(dirname "$0")/.."

PORT="${PORT:-8080}"
export SERVER_PORT="$PORT"
export DEMO_DATA_ENABLED="${DEMO_DATA_ENABLED:-true}"
if [[ -n "${SPRING_PROFILE:-}" ]]; then
  export SPRING_PROFILES_ACTIVE="$SPRING_PROFILE"
fi

echo "Starting Certificate Expiry Monitor on http://localhost:${PORT}"
./mvnw -B spring-boot:run

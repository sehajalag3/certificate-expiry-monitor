#!/usr/bin/env bash
# Runs the full test suite (Linux/macOS). Add --ui for the Selenium journeys.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Running unit and integration tests..."
./mvnw -B test

if [[ "${1:-}" == "--ui" ]]; then
  echo "Running Selenium journeys (requires Google Chrome)..."
  ./mvnw -B test -Pui "${UI_HEADLESS:+-Dui.headless=$UI_HEADLESS}"
fi

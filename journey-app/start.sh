#!/usr/bin/env bash
set -euo pipefail

# Must be run from the journey-app folder
ROOT_DIR="$(cd .. && pwd)"
# shellcheck disable=SC1090
source "${ROOT_DIR}/dev/common.sh"

ENV_FILE="./.env"

ensure_docker_ready
require_file "./docker-compose.yml"
require_file "${ENV_FILE}"

echo "[journey-app] Starting API layer..."
compose_up_here "${ENV_FILE}" "true"

echo "[journey-app] Waiting for API health on http://localhost:8080/..."
for i in {1..60}; do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "[journey-app] API is healthy."
    exit 0
  fi
  sleep 2
done

echo "[journey-app] ERROR: API did not become healthy in time."
exit 1

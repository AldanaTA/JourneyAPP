#!/usr/bin/env bash
set -euo pipefail

# Must be run from agent-services folder
ROOT_DIR="$(cd .. && pwd)"
# shellcheck disable=SC1090
source "${ROOT_DIR}/dev/common.sh"

ENV_FILE="./.env"

ensure_docker_ready
require_file "./docker-compose.yml"
require_file "${ENV_FILE}"

echo "[agent-services] Starting agent services..."
compose_up_here "${ENV_FILE}" "true"

echo "[agent-services] Done."

#!/usr/bin/env bash
set -euo pipefail

# Must be run from backend folder
ROOT_DIR="$(cd .. && pwd)"
# shellcheck disable=SC1090
source "${ROOT_DIR}/dev/common.sh"

ENV_FILE="./.env"
SCHEMA_DIR="./schema"

ensure_docker_ready
require_file "./docker-compose.yml"
require_file "${ENV_FILE}"

echo "[backend] Starting Postgres..."
compose_up_here "${ENV_FILE}" "false"

# Load env vars for applying schema
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

DB_CONTAINER="${DB_CONTAINER_NAME:-postgres}"

echo "[backend] Waiting for Postgres readiness in container '${DB_CONTAINER}'..."
for i in {1..60}; do
  if docker exec "${DB_CONTAINER}" pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
    echo "[backend] Postgres is ready."
    break
  fi
  sleep 1
done

docker exec "${DB_CONTAINER}" pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1 || {
  echo "[backend] ERROR: Postgres did not become ready in time."
  exit 1
}

if [[ ! -d "${SCHEMA_DIR}" ]]; then
  echo "[backend] No schema directory found at ${SCHEMA_DIR}. Skipping SQL apply."
  exit 0
fi

shopt -s nullglob
SQL_FILES=("${SCHEMA_DIR}"/*.sql)

if (( ${#SQL_FILES[@]} == 0 )); then
  echo "[backend] No .sql files found in ${SCHEMA_DIR}. Done."
  exit 0
fi

echo "[backend] Applying schema SQL files (lexical order):"
for f in "${SQL_FILES[@]}"; do
  echo "[backend] -> $(basename "$f")"
  docker exec -i "${DB_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" < "$f"
done

echo "[backend] Done."

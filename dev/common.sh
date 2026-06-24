#!/usr/bin/env bash
set -euo pipefail

# --------------------------------------------------
# Ensure Docker is installed and running
# --------------------------------------------------
ensure_docker_ready() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "[common] ERROR: Docker is not installed."
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    echo "[common] ERROR: Docker is not running."
    exit 1
  fi
}

# --------------------------------------------------
# Ensure a required file exists
# --------------------------------------------------
require_file() {
  local file="$1"

  if [[ ! -f "$file" ]]; then
    echo "[common] ERROR: Required file '$file' not found."
    exit 1
  fi
}

# --------------------------------------------------
# Ensure a required directory exists
# --------------------------------------------------
require_dir() {
  local dir="$1"

  if [[ ! -d "$dir" ]]; then
    echo "[common] ERROR: Required directory '$dir' not found."
    exit 1
  fi
}

# --------------------------------------------------
# Bring up docker compose in current directory
# Arg1: env file path
# Arg2: build (true/false)
# --------------------------------------------------
compose_up_here() {
  local env_file="$1"
  local build="${2:-false}"

  if [[ "$build" == "true" ]]; then
    docker compose --env-file "$env_file" up -d --build
  else
    docker compose --env-file "$env_file" up -d
  fi
}

# --------------------------------------------------
# Wait for Postgres container readiness
# Arg1: container name
# Arg2: db user
# Arg3: db name
# --------------------------------------------------
wait_for_postgres() {
  local container="$1"
  local user="$2"
  local db="$3"

  echo "[common] Waiting for Postgres in container '$container'..."

  for i in {1..60}; do
    if docker exec "$container" pg_isready -U "$user" -d "$db" >/dev/null 2>&1; then
      echo "[common] Postgres is ready."
      return 0
    fi
    sleep 1
  done

  echo "[common] ERROR: Postgres did not become ready in time."
  exit 1
}

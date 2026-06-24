#!/usr/bin/env bash
set -euo pipefail

require_root_or_sudo() {
  if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    if ! command -v sudo >/dev/null 2>&1; then
      echo "[setup] ERROR: sudo not found and you are not root. Install sudo or run as root."
      exit 1
    fi
  fi
}

run_as_root() {
  if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
    "$@"
  else
    sudo "$@"
  fi
}

ensure_docker_installed() {
  if command -v docker >/dev/null 2>&1; then
    return 0
  fi

  echo "[setup] Docker not found. Installing Docker Engine + Compose plugin..."
  require_root_or_sudo

  # Ensure lsb_release exists to detect Ubuntu codename
  if ! command -v lsb_release >/dev/null 2>&1; then
    run_as_root apt-get update -y
    run_as_root apt-get install -y lsb-release
  fi

  local codename
  codename="$(lsb_release -cs)"

  run_as_root apt-get update -y
  run_as_root apt-get install -y ca-certificates curl gnupg

  run_as_root install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | run_as_root gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  run_as_root chmod a+r /etc/apt/keyrings/docker.gpg

  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
    ${codename} stable" | run_as_root tee /etc/apt/sources.list.d/docker.list >/dev/null

  run_as_root apt-get update -y
  run_as_root apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

  run_as_root systemctl enable --now docker

  # Add current user to docker group (effective after re-login)
  if ! id -nG "$USER" | grep -qw docker; then
    echo "[setup] Adding user '$USER' to docker group (effective after log out/in)..."
    run_as_root usermod -aG docker "$USER" || true
  fi

  echo "[setup] Docker installed."
}

ensure_compose_available() {
  if docker compose version >/dev/null 2>&1; then
    return 0
  fi

  echo "[setup] docker compose plugin not available. Installing compose plugin..."
  require_root_or_sudo
  run_as_root apt-get update -y
  run_as_root apt-get install -y docker-compose-plugin

  docker compose version >/dev/null 2>&1 || {
    echo "[setup] ERROR: docker compose still not available after install."
    exit 1
  }
}

ensure_docker_ready() {
  ensure_docker_installed
  ensure_compose_available

  if ! docker info >/dev/null 2>&1; then
    echo "[setup] Docker daemon not responding. Trying to start docker service..."
    require_root_or_sudo
    run_as_root systemctl start docker
  fi

  docker info >/dev/null 2>&1 || {
    echo "[setup] ERROR: Docker is installed but not usable (permission/daemon issue)."
    echo "        If permission denied: log out/in after being added to docker group, or run with sudo."
    exit 1
  }
}

require_file() {
  local f="$1"
  if [[ ! -f "$f" ]]; then
    echo "[error] Missing required file: $f"
    exit 1
  fi
}

compose_up_here() {
  local env_file="$1"
  local do_build="${2:-false}"

  if [[ "$do_build" == "true" ]]; then
    docker compose --env-file "$env_file" up -d --build
  else
    docker compose --env-file "$env_file" up -d
  fi
}

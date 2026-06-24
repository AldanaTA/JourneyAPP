#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd .. && pwd)"

echo "======================================"
echo " Starting full stack (DEV)"
echo "======================================"

echo ""
echo ">>> Starting backend (Postgres + schema)"
cd "${ROOT_DIR}/backend"
./start.sh

echo ""
echo ">>> Starting API layer"
cd "${ROOT_DIR}/api-layer"
./start.sh

echo ""
echo ">>> Starting Agent services"
cd "${ROOT_DIR}/agent-services"
./start.sh

echo ""
echo "======================================"
echo " All services started successfully"
echo "======================================"

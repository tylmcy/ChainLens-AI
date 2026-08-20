#!/bin/sh
set -eu

PROJECT_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

docker compose --profile demo-offline up --build -d
"$PROJECT_ROOT/scripts/demo-verify.sh"

echo
echo "ChainLens M4 本地演示已启动："
echo "  API:     http://localhost:${CHAINLENS_PORT:-8080}/api/v1/analyses"
echo "  Swagger: http://localhost:${CHAINLENS_PORT:-8080}/swagger-ui.html"
echo "  场景:    $PROJECT_ROOT/data/anvil-demo/transactions.json"
echo "停止并保留数据库：docker compose down"
echo "停止并清空演示数据库：docker compose down -v"

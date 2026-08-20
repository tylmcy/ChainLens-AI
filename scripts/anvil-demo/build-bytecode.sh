#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
mkdir -p "$SCRIPT_DIR/build"

# 官方 solc 镜像目前以 linux/amd64 发布；这里只在修改合约源码后用于重新生成字节码，
# 日常 M4 演示使用仓库内已固定的 .bin，仍由原生 ARM64 Foundry 运行。
docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  -v "$SCRIPT_DIR:/sources" \
  ethereum/solc:0.8.30 \
  --bin --optimize --optimize-runs 200 --overwrite \
  -o /sources/build /sources/src/DemoTransactions.sol

echo "已生成：$SCRIPT_DIR/build/DemoTransactions.bin"

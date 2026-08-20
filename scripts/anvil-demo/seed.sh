#!/bin/sh
set -eu

RPC_URL="${ETH_RPC_URL:-http://anvil:8545}"
OUTPUT_DIR="${DEMO_OUTPUT_DIR:-/demo-output}"
SENDER="0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266"
EOA_TARGET="0x70997970c51812dc3a010c7d01b50e0d17dc79c8"
RISK_ADDRESS="0xdead000000000000000000000000000000000001"
MAX_UINT256="115792089237316195423570985008687907853269984665640564039457584007913129639935"

until cast chain-id --no-proxy --rpc-url "$RPC_URL" >/dev/null 2>&1; do
  echo "等待 Anvil JSON-RPC：$RPC_URL"
  sleep 1
done

# 每次造数前清空本地链，保证合约地址和交易哈希完全可复现。
cast rpc --rpc-url "$RPC_URL" --no-proxy anvil_reset >/dev/null

echo "部署本地演示合约……"
BYTECODE="0x$(tr -d '\n' < /demo/build/DemoTransactions.bin)"
DEPLOY_HASH="$(cast send --rpc-url "$RPC_URL" --no-proxy --from "$SENDER" \
  --unlocked --async --create "$BYTECODE")"
DEPLOY_RECEIPT="$(cast receipt --rpc-url "$RPC_URL" --no-proxy "$DEPLOY_HASH")"
CONTRACT_ADDRESS="$(printf '%s\n' "$DEPLOY_RECEIPT" | sed -n 's/^contractAddress[[:space:]]*//p')"
if [ -z "$CONTRACT_ADDRESS" ]; then
  printf '%s\n' "$DEPLOY_RECEIPT"
  echo "无法从部署回执读取合约地址" >&2
  exit 1
fi

send() {
  cast send --rpc-url "$RPC_URL" --no-proxy --from "$SENDER" --unlocked --async "$@"
}

UNLIMITED_APPROVAL_HASH="$(send "$CONTRACT_ADDRESS" 'approve(address,uint256)' "$EOA_TARGET" "$MAX_UINT256")"
ZERO_TRANSFER_HASH="$(send "$CONTRACT_ADDRESS" 'burn(uint256)' 1)"
MANY_TRANSFERS_HASH="$(send "$CONTRACT_ADDRESS" 'transfer(address,uint256)' 0x0000000000000000000000000000000000000100 21)"
FAILED_HASH="$(cast send --rpc-url "$RPC_URL" --no-proxy --from "$SENDER" --unlocked \
  --async --gas-limit 300000 "$CONTRACT_ADDRESS" 'mint(address,uint256)' "$EOA_TARGET" 1)"
EOA_INTERACTION_HASH="$(send "$EOA_TARGET" --data "$(cast calldata 'transfer(address,uint256)' "$SENDER" 1)")"
UNKNOWN_SELECTOR_HASH="$(send "$CONTRACT_ADDRESS" --data 0xdeadbeef)"
RISK_ADDRESS_HASH="$(send "$RISK_ADDRESS" --value 1wei)"

# --async 只返回 hash；等待最后一笔回执，确保所有场景都已被即时出块。
cast receipt --rpc-url "$RPC_URL" --no-proxy "$RISK_ADDRESS_HASH" >/dev/null

mkdir -p "$OUTPUT_DIR"
cat > "$OUTPUT_DIR/transactions.json" <<EOF
{
  "network": "anvil-local",
  "chainId": 31337,
  "contractAddress": "$CONTRACT_ADDRESS",
  "notice": "地址与资产仅属于本地 Anvil 内存链，不可用于任何公共网络。",
  "scenarios": [
    {"id":"unlimited-approval","txHash":"$UNLIMITED_APPROVAL_HASH","expectedRuleIds":["ERC20-UNLIMITED-APPROVAL"]},
    {"id":"failed-transaction","txHash":"$FAILED_HASH","expectedRuleIds":["TX-FAILED"]},
    {"id":"transfer-to-zero","txHash":"$ZERO_TRANSFER_HASH","expectedRuleIds":["TRANSFER-TO-ZERO-ADDRESS"]},
    {"id":"interaction-with-eoa","txHash":"$EOA_INTERACTION_HASH","expectedRuleIds":["INTERACTION-WITH-EOA"]},
    {"id":"unknown-selector","txHash":"$UNKNOWN_SELECTOR_HASH","expectedRuleIds":["UNKNOWN-SELECTOR"]},
    {"id":"many-token-transfers","txHash":"$MANY_TRANSFERS_HASH","expectedRuleIds":["MANY-TOKEN-TRANSFERS"]},
    {"id":"risk-address-hit","txHash":"$RISK_ADDRESS_HASH","expectedRuleIds":["RISK-ADDRESS-HIT"]}
  ]
}
EOF

echo "已生成 7 个确定性演示场景：$OUTPUT_DIR/transactions.json"

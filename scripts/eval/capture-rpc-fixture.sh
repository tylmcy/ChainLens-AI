#!/bin/sh
set -eu

# 只读采集一笔交易的原始 JSON-RPC envelope；调用方负责选择 Anvil 或公开历史交易。
# 用法：ETH_RPC_URL=... capture-rpc-fixture.sh case-id 0x交易哈希 输出目录
CASE_ID="${1:?需要 case-id}"
TX_HASH="${2:?需要交易哈希}"
OUTPUT_ROOT="${3:?需要输出目录}"
RPC_URL="${ETH_RPC_URL:?需要 ETH_RPC_URL 环境变量}"
RPC_URLS="$RPC_URL ${ETH_RPC_FALLBACK_URLS:-}"

case "$CASE_ID" in
  *[!a-z0-9-]* | "") echo "case-id 只能包含小写字母、数字和连字符" >&2; exit 2 ;;
esac
case "$TX_HASH" in
  0x*) [ "${#TX_HASH}" -eq 66 ] || { echo "$CASE_ID 交易哈希长度错误" >&2; exit 2; } ;;
  *) echo "$CASE_ID 交易哈希格式错误" >&2; exit 2 ;;
esac

mkdir -p "$OUTPUT_ROOT"
CASE_DIR="$OUTPUT_ROOT/$CASE_ID"
if [ -e "$CASE_DIR" ]; then
  echo "$CASE_ID fixture 已存在，拒绝覆盖：$CASE_DIR" >&2
  exit 2
fi
mkdir "$CASE_DIR"

rpc() {
  method="$1"
  params="$2"
  output="$3"
  allow_error="${4:-false}"
  body="$(jq -cn --arg method "$method" --argjson params "$params" \
    '{jsonrpc:"2.0",id:1,method:$method,params:$params}')"
  attempt=1
  while [ "$attempt" -le 4 ]; do
    for endpoint in $RPC_URLS; do
      if curl --noproxy '*' --silent --show-error --fail --max-time 30 \
          -H 'Content-Type: application/json' --data "$body" "$endpoint" --output "$output" \
          && jq -e --argjson allowError "$allow_error" \
            '.jsonrpc == "2.0" and ((has("result") and .result != null) or ($allowError and has("error")))' \
            "$output" >/dev/null 2>&1; then
        return 0
      fi
    done
    echo "$CASE_ID $method 第 $attempt 轮响应不可用" >&2
    attempt=$((attempt + 1))
    [ "$attempt" -le 4 ] && sleep 2
  done
  echo "$CASE_ID $method 响应无效或连续限流" >&2
  exit 1
}

rpc eth_chainId '[]' "$CASE_DIR/chain-id.json"
rpc eth_getTransactionByHash "$(jq -cn --arg hash "$TX_HASH" '[$hash]')" "$CASE_DIR/transaction.json"
rpc eth_getTransactionReceipt "$(jq -cn --arg hash "$TX_HASH" '[$hash]')" "$CASE_DIR/receipt.json"

jq -e --arg hash "$TX_HASH" '(.result.hash | ascii_downcase) == ($hash | ascii_downcase)' \
  "$CASE_DIR/transaction.json" >/dev/null || { echo "$CASE_ID 交易响应哈希不匹配" >&2; exit 1; }
jq -e --arg hash "$TX_HASH" '(.result.transactionHash | ascii_downcase) == ($hash | ascii_downcase)' \
  "$CASE_DIR/receipt.json" >/dev/null || { echo "$CASE_ID 回执哈希不匹配" >&2; exit 1; }

block_number="$(jq -r '.result.blockNumber // empty' "$CASE_DIR/receipt.json")"
[ -n "$block_number" ] || { echo "$CASE_ID 回执缺少区块号" >&2; exit 1; }
rpc eth_getBlockByNumber "$(jq -cn --arg number "$block_number" '[$number,false]')" "$CASE_DIR/block.json"

to_address="$(jq -r '.result.to // empty' "$CASE_DIR/transaction.json")"
if [ -n "$to_address" ] && [ "$(printf '%s' "$to_address" | tr '[:upper:]' '[:lower:]')" \
    != '0x0000000000000000000000000000000000000000' ]; then
  rpc eth_getCode "$(jq -cn --arg address "$to_address" --arg number "$block_number" \
    '[$address,$number]')" "$CASE_DIR/code.json" true
fi

echo "已冻结 ${CASE_ID}：${TX_HASH}"

#!/bin/sh
set -eu

PROJECT_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
MANIFEST="$PROJECT_ROOT/data/anvil-demo/transactions.json"
BASE_URL="http://localhost:${CHAINLENS_PORT:-8080}"

if [ ! -f "$MANIFEST" ]; then
  echo "演示清单不存在：$MANIFEST" >&2
  exit 1
fi

attempt=0
until curl --silent --fail "$BASE_URL/v3/api-docs" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 90 ]; then
    echo "ChainLens 应用在 90 秒内未就绪" >&2
    exit 1
  fi
  sleep 1
done

failures=0
while IFS='|' read -r scenario tx_hash rule_id; do
  [ -n "$scenario" ] || continue
  response="$(curl --silent --fail \
    -H 'Content-Type: application/json' \
    -d "{\"txHash\":\"$tx_hash\"}" \
    "$BASE_URL/api/v1/analyses")"
  if printf '%s' "$response" | grep -q "\"ruleId\":\"$rule_id\""; then
    echo "通过：$scenario -> $rule_id"
  else
    echo "失败：$scenario 未命中 $rule_id" >&2
    failures=$((failures + 1))
  fi
done <<EOF
$(sed -n 's/.*{"id":"\([^"]*\)","txHash":"\([^"]*\)","expectedRuleIds":\["\([^"]*\)"\]}.*/\1|\2|\3/p' "$MANIFEST")
EOF

if [ "$failures" -ne 0 ]; then
  echo "M4 演示验收失败：$failures 个场景未命中预期规则" >&2
  exit 1
fi

echo "M4 演示验收通过：7 条规则均由真实 Anvil 交易触发。"

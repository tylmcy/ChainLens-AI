# M8 MCP 固定 Anvil 演示

这份演示只使用 Anvil 内存链和公开测试账户，不需要真实私钥、助记词或真实资产。

## 1. 启动与取得固定案例

```bash
./scripts/demo-up.sh
sed -n '1,80p' data/anvil-demo/transactions.json
```

选择 `unlimited-approval` 的 `txHash`。每次 Anvil 重建后都以当次 `transactions.json` 为准，不要把历史哈希当成公网交易。

## 2. 用 Inspector 列表和调用

```bash
npx --yes @modelcontextprotocol/inspector@2.5.0 --cli \
  --transport http \
  --server-url http://127.0.0.1:8080/mcp \
  --method tools/list
```

打开图形 Inspector 后选择 `analyzeTransaction`，将上一步的哈希填入 `txHash`。结果中应同时看到：

- `ruleId: ERC20-UNLIMITED-APPROVAL`；
- `confidence: 1.0`；
- `evidence.rpcMethod: eth_getTransactionReceipt`；
- `evidence.rawJsonRef.jsonPointer: /result/logs/0`；
- `sources[].type: CHAIN`；
- 未启用 LLM/RAG 时的 `llmUsed: false`、`degraded: true` 和 limitation。

## 3. 脱敏后的精简输出

```json
{
  "txHash": "0x<ANVIL_TRANSACTION_HASH>",
  "network": "ANVIL",
  "report": {
    "status": "SUCCESS",
    "findings": [
      {
        "ruleId": "ERC20-UNLIMITED-APPROVAL",
        "severity": "HIGH",
        "confidence": 1.0,
        "evidence": {
          "transactionHash": "0x<ANVIL_TRANSACTION_HASH>",
          "rpcMethod": "eth_getTransactionReceipt",
          "rawJsonRef": {
            "jsonPointer": "/result/logs/0"
          }
        }
      }
    ],
    "sources": [
      {
        "type": "CHAIN",
        "label": "回执",
        "reference": "eth_getTransactionReceipt / 0x<ANVIL_TRANSACTION_HASH>"
      }
    ],
    "limitations": [
      "本报告由确定性模板生成，未使用 LLM（llmUsed=false）。",
      "RAG 未启用，因此没有伪造 KNOWLEDGE 来源。"
    ]
  },
  "llmUsed": false,
  "degraded": true,
  "persisted": false
}
```

`CHAIN` 是当前交易的证据来源。`KNOWLEDGE` 只在启用 RAG 且本次真实检索到知识片段时出现；不能为了让字段“看起来完整”而伪造。单独调用 `searchKnowledge` 可看到知识来源：

```json
{
  "status": "AVAILABLE",
  "reason": "",
  "hits": [
    {
      "source": "docs/knowledge/erc20-unlimited-approval.md",
      "title": "ERC-20 无限额度授权",
      "ruleIds": ["ERC20-UNLIMITED-APPROVAL"],
      "similarity": 0.91
    }
  ]
}
```

## 4. 无效参数排错

把 `txHash` 改成 `0x1234`。预期得到 `INVALID_INPUT`，而不是空报告、伪造事实或 Java 堆栈。

# M9 Anvil 示例报告：无限额度授权

这份示例来自本地 Anvil 测试链，不涉及真实资产。2026-10-19 使用 `POST /api/v1/analyses` 查询下列交易后，从真实 API 响应摘录；为便于阅读，省略完整 `facts.transaction.rawJson`、`facts.receipt.rawJson` 和区块原文。它展示的是**无模型 Key 的降级路径**，不是 AI 已成功调用的证明。

```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"txHash":"0x924318416f8abc744d48948137ed7aee65715388a95198ff46834a4fd1a7c1e3"}' \
  http://127.0.0.1:8080/api/v1/analyses
```

| 字段 | 实际结果 |
|---|---|
| 网络 | `ANVIL`，chainId `31337` |
| 交易哈希 | `0x924318416f8abc744d48948137ed7aee65715388a95198ff46834a4fd1a7c1e3` |
| 执行状态 | `SUCCESS` |
| 操作 | ERC-20 `approve(address,uint256)` |
| 授权对象 | `0x70997970c51812dc3a010c7d01b50e0d17dc79c8` |
| 授权额度 | `115792089237316195423570985008687907853269984665640564039457584007913129639935`（`2^256-1`） |
| 风险结论 | `ERC20-UNLIMITED-APPROVAL`，`HIGH`，`confidence=1.0` |
| 模型状态 | `llmUsed=false`，`degraded=true`；确定性结论仍可用 |

报告摘要：该交易执行成功，发生一次 ERC-20 授权，规则引擎识别出无限额度授权。这里的 `confidence=1.0` 表示该确定性规则对所见事件的匹配置信度，**不等于合约或授权对象绝对安全/危险的概率**。

关键证据来自 `eth_getTransactionReceipt`：区块 `2` 的 `logs[0]` 是 `Approval` 事件，`topics[1]` 是 owner，`topics[2]` 是 spender，`data` 为 `0xffff...ffff`。报告中的原始定位是 `rawJsonRef.rpcMethod=eth_getTransactionReceipt` 与 `rawJsonRef.jsonPointer=/result/logs/0`，并带有 `transactionHash` 和 `logIndex=0`。因此读者可以从结论回到具体的 RPC 原文，而不必相信模型的口头解释。

局限：这是本地测试场景；示例没有调用 LLM，也没有证明真实主网交易风险。本工具不是投资建议或完整安全审计。

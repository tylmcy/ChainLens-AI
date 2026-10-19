# deterministic-risk-analysis Specification Delta

## MODIFIED Requirements

### Requirement: 风险结论携带结构化依据
每项 finding MUST 包含稳定的 ruleId、title、severity、confidence 和 evidence；evidence MUST 包含支持结论的原始值和可复核位置。风险地址命中时，原始位置 MUST 指向实际命中的参与方字段或日志 topic，不得以未命中的交易字段作为证据。

#### Scenario: 检测到无限额度授权
- **WHEN** Approval 事件的授权值等于 uint256 最大值
- **THEN** finding 使用 `ERC20-UNLIMITED-APPROVAL`，并在 evidence 中保留 token、spender、rawValue、交易哈希和日志位置

#### Scenario: 风险列表命中交易接收方
- **WHEN** 风险列表中的地址与交易 `to` 相同、与 `from` 不同
- **THEN** `RISK-ADDRESS-HIT` 的 evidence 指向 `eth_getTransactionByHash` 原始响应的 `/result/to`

#### Scenario: 风险列表命中事件参与方
- **WHEN** 风险列表中的地址只出现在已解码 Transfer 或 Approval 事件的 indexed topic
- **THEN** `RISK-ADDRESS-HIT` 的 evidence 指向相应 receipt 日志的该 topic，并保留该日志的 `logIndex`

#### Scenario: 无 ABI 的已知函数选择器碰撞
- **WHEN** calldata selector 为 `0x42966c68` 且没有可信 ABI 可将候选缩减为唯一签名
- **THEN** calldata 状态为 `AMBIGUOUS`，列出 `burn(uint256)` 与 `collate_propagate_storage(bytes16)`，不得仅凭 selector 断言已经唯一解析

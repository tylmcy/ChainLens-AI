# Spec Delta

## ADDED Requirements

### Requirement: 知识检索不得改变确定性事实
系统 MAY 使用受控知识检索结果解释规则、术语和防范建议，但 MUST 仅使用 TxFacts、原始 JSON-RPC 和确定性 findings 证明交易事实、ruleId、confidence 与 evidence 原始定位。

#### Scenario: 知识片段包含交易样例或数字
- **WHEN** 模型读取的知识片段包含示例地址、哈希、金额或风险描述
- **THEN** 事实一致性校验不把这些内容视为当前交易证据，也不允许它们产生当前交易的 finding

#### Scenario: 报告引用知识解释
- **WHEN** 报告使用检索片段解释已由确定性规则确认的 finding
- **THEN** 报告在 sources 中增加 `KNOWLEDGE` 来源，同时保留该 finding 的 ruleId、confidence 和链上 evidence

#### Scenario: 知识库不可用或没有命中
- **WHEN** 知识检索返回不可用状态或空命中列表
- **THEN** Agent 不伪造知识来源，并继续生成仅基于确定性事实的报告，在 limitations 中说明知识增强未使用


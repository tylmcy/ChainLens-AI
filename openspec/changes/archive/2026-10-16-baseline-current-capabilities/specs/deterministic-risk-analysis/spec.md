# Spec Delta

## Purpose

定义不依赖语言模型的交易解析与风险判定契约，确保同一份链上事实产生可复现、可解释且可测试的风险结论。

## ADDED Requirements

### Requirement: 执行七条确定性规则
系统 SHALL 对交易事实执行无限额度授权、执行失败、零地址转账或销毁、与 EOA 异常交互、未知方法选择器、大量 Token 转移和风险地址命中七类规则。

#### Scenario: 交易触发一条或多条规则
- **WHEN** 交易事实满足任意规则的确定条件
- **THEN** 系统为每条满足的规则分别输出 finding，且结果不依赖 LLM 判断

#### Scenario: 交易不满足规则
- **WHEN** 交易事实不满足某条规则的完整条件
- **THEN** 系统不得仅因模型推测或描述相似而输出该规则 finding

### Requirement: 风险结论携带结构化依据
每项 finding MUST 包含稳定的 ruleId、title、severity、confidence 和 evidence；evidence MUST 包含支持结论的原始值和可复核位置。

#### Scenario: 检测到无限额度授权
- **WHEN** Approval 事件的授权值等于 uint256 最大值
- **THEN** finding 使用 `ERC20-UNLIMITED-APPROVAL`，并在 evidence 中保留 token、spender、rawValue、交易哈希和日志位置

### Requirement: 规则执行可复现
系统 SHALL 对相同版本的规则和相同交易事实产生相同的 finding 集合，并允许在没有 LLM、外部知识库或公网连接时独立运行。

#### Scenario: 离线重复分析相同事实
- **WHEN** 确定性引擎两次接收相同的交易事实和风险地址配置
- **THEN** 两次输出的 ruleId、严重级别和证据内容一致

# transaction-evaluation Specification

## Purpose

定义 ChainLens 对 Ethereum 单笔交易解析、确定性风险规则和可选 Agent 链路进行可复现评测的行为契约，使准确率、事实一致性、延迟与成本都有可审计证据。

## Requirements

### Requirement: 提供版本化的三十条交易评测集
系统 SHALL 提供恰好 30 条 V1 基准用例，其中至少 15 条为可复现 Anvil 构造交易、至少 15 条为公开 Ethereum 历史交易的冻结 JSON-RPC 响应；每条用例 MUST 包含稳定 `caseId`、数据集版本、网络、交易哈希、来源说明、预期状态、ETH 变化、事件、calldata 三态结果和七条规则的预期命中集合。

#### Scenario: 校验完整评测集
- **WHEN** 执行器加载 V1 数据集
- **THEN** 它确认用例总数、两类来源数量、唯一 `caseId`、必填 ground truth、fixture 完整性和来源元数据均满足契约，否则在分析前失败

#### Scenario: 审查历史交易来源
- **WHEN** 用户查看任一公开历史交易用例
- **THEN** 用例能说明网络、交易哈希、冻结数据的取得来源与日期，且不包含私钥、助记词、真实资产凭据或模型密钥

### Requirement: Offline 模式回放生产解析与规则能力
执行器 SHALL 默认以 offline 模式运行，使用冻结 JSON-RPC fixture 驱动现有交易取证与解析代码，并将产生的 `TxFacts` 交给现有确定性规则引擎；该模式 MUST NOT 访问公网 RPC、调用 LLM、要求数据库或复制另一套解析和规则实现。

#### Scenario: 在断网且没有密钥时评测
- **WHEN** 环境未配置 RPC、数据库和模型凭据并启动 offline 评测
- **THEN** 全部 30 条用例仍可完成，且同一 commit、数据集和规则配置的重复执行产生相同的解析与 finding 结果

#### Scenario: Fixture 不完整或互相矛盾
- **WHEN** 用例缺少取证所需的 chainId、transaction、receipt、block 或适用的 code 响应，或响应与 ground truth 的交易哈希不一致
- **THEN** 执行器明确指出 `caseId` 和缺失或冲突位置并失败，不把缺失事实补成成功结果

### Requirement: 机械计算 Offline 准确率和门槛
执行器 MUST 逐字段比较交易状态、ETH 变化、事件集合及 calldata 的 `RESOLVED`、`AMBIGUOUS`、`UNRESOLVED` 三态，按“正确字段数 / 纳入比较字段总数”计算解析准确率；它 MUST 在每条用例与七条规则的笛卡尔集合上比较是否命中，按 `(TP+TN)/(TP+TN+FP+FN)` 计算规则准确率。Offline 验收门槛 SHALL 为解析准确率不低于 98% 且规则准确率等于 100%。

#### Scenario: 全部结果达到门槛
- **WHEN** 所有必填字段完成比较，解析准确率至少为 98% 且规则准确率为 100%
- **THEN** offline 执行以成功状态结束并在报告中列出分子、分母、百分比和阈值

#### Scenario: 任一指标未达到门槛
- **WHEN** 解析准确率低于 98% 或规则准确率低于 100%
- **THEN** offline 执行以非零状态结束，并列出每个失败 `caseId` 的字段或规则、期望值、实际值及 TP、TN、FP 或 FN 分类

### Requirement: 保存可追溯的评测差异和报告
每次评测 SHALL 生成机器可读 JSON 和便于阅读的 Markdown 摘要，并记录执行时间、Git commit、数据集版本、规则版本、执行模式和各指标计算明细。涉及 finding 的差异 MUST 保留 `ruleId`、`confidence`、`evidence` 及原始字段路径或日志索引；执行器不得用知识库或模型文本作为当前交易的事实证据。

#### Scenario: 定位规则误差
- **WHEN** 某条用例出现错误命中、遗漏命中或 finding 结构不一致
- **THEN** JSON 与 Markdown 结果都能通过 `caseId` 定位该差异，且机器报告保存期望与实际的 `ruleId`、`confidence`、`evidence` 和原始数据位置

#### Scenario: Git commit 不可取得
- **WHEN** 执行环境无法解析 Git commit
- **THEN** 报告将 commit 明确标记为不可用并保留其他复现元数据，而不是填入伪造值

### Requirement: Full 模式统计模型链路指标并控制费用
执行器 SHALL 提供显式选择的 full 模式，对同一版本数据集统计工具调用成功率、报告事实一致率、端到端及 RPC/解析/规则/LLM 分段的 P50/P95 延迟，以及 token 和可配置单价计算出的单笔与总成本。工具调用成功率门槛 SHALL 不低于 95%，事实一致率门槛 SHALL 不低于 98%；延迟只记录基线，成本 MUST 受运行前配置的预算上限约束。

#### Scenario: 完成 Full 评测
- **WHEN** 操作者显式选择 full 模式并提供可用的 RPC 与模型配置
- **THEN** 报告包含六项指标、模型名、provider、配置单价、预算、样本数和失败用例明细

#### Scenario: 预计或累计成本触及预算
- **WHEN** full 评测的累计成本达到配置预算上限
- **THEN** 执行器停止发起新的模型调用，以非成功状态结束，并报告已完成用例、未执行用例和实际累计成本

#### Scenario: 模型或 RPC 不可用
- **WHEN** full 模式中的模型或公网 RPC 调用失败且重试策略耗尽
- **THEN** 执行器记录真实失败及对应指标，不用模板报告或虚构调用把该次执行记为成功

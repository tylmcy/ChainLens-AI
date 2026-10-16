# agent-report-orchestration Specification

## Purpose

定义语言模型可用时的只读工具编排、结构化中文报告护栏，以及模型不可用或输出不可信时的确定性降级行为。

## Requirements

### Requirement: ChatModel 可用时执行只读工具循环
系统 SHALL 仅向 Agent 暴露只读分析工具，并限制工具循环轮数；工具集不得包含发送交易、签名或读取秘密的能力。

#### Scenario: 模型请求已注册工具
- **WHEN** ChatModel 返回合法的工具调用请求
- **THEN** 系统校验参数、执行对应只读工具、记录调用结果并将结果返回模型

#### Scenario: 模型持续请求工具
- **WHEN** 工具调用达到配置的最大轮数
- **THEN** 系统停止继续循环并转入受控报告生成或降级流程

### Requirement: 强制装配确定性事实
系统 MUST 在报告生成前保证模型上下文包含交易事实和规则 findings，即使模型没有主动调用必需工具。

#### Scenario: 模型未调用必需工具
- **WHEN** 工具循环结束时缺少交易事实或风险 findings
- **THEN** 系统自动加入对应的确定性工具结果后再请求报告

### Requirement: 校验报告结构与事实一致性
系统 MUST 校验模型报告的结构以及其中的哈希、地址、数字、ruleId 和重要 evidence 是否能由确定性事实支持；重要结论必须包含 confidence 和原始数据定位信息。

#### Scenario: 模型报告引用不存在的事实
- **WHEN** 报告包含无法在交易事实或规则 findings 中验证的哈希、数值或规则结论
- **THEN** 系统拒绝该报告并按受控策略重试或降级

#### Scenario: 模型报告不符合结构要求
- **WHEN** 报告无法解析为目标结构或缺少必要字段
- **THEN** 系统最多重试一次，仍失败则返回确定性模板报告

### Requirement: 无模型时保持核心能力可用
系统 SHALL 在没有可用 ChatModel、模型调用失败或模型输出未通过护栏时生成中文确定性报告，并标记 `llmUsed:false` 与 `degraded:true`。

#### Scenario: 未配置模型 Key
- **WHEN** 应用启动时没有可用 ChatModel
- **THEN** 交易取证、解析、风险规则、历史保存和报告返回仍然可用

### Requirement: 暴露工具调用可观测信息
系统 SHALL 保存或返回工具调用步骤，并通过指标区分每个工具调用的成功与失败。

#### Scenario: 工具调用完成
- **WHEN** Agent 工具执行成功或失败
- **THEN** 系统记录工具名称、结果状态和可用于计算成功率的指标标签

# analysis-api-history Specification

## Purpose

定义用户和演示客户端如何通过 REST 与 SSE 发起交易分析、观察处理进度，并从 PostgreSQL 读取可重放的历史结果。

## Requirements

### Requirement: 提供同步分析接口
系统 SHALL 接受交易哈希分析请求并返回结构化中文报告；响应必须表明是否使用 LLM 以及是否发生降级。

#### Scenario: 请求 JSON 分析
- **WHEN** 客户端向 `/api/v1/analyses` 提交有效交易哈希并请求 JSON
- **THEN** 系统完成分析并返回报告、findings、sources、limitations、工具步骤和降级状态

#### Scenario: 请求参数无效
- **WHEN** 客户端提交空值或格式错误的交易哈希
- **THEN** 系统返回稳定的客户端错误，不调用 Ethereum RPC

### Requirement: 通过 SSE 展示分析进度
系统 SHALL 在客户端请求事件流时发送阶段进度；Agent 调用工具时 SHALL 暴露 tool.started 和 tool.completed 事件。

#### Scenario: 客户端订阅实时分析
- **WHEN** 客户端以 `Accept: text/event-stream` 提交分析
- **THEN** 系统按执行顺序发送进度事件并以最终报告或明确错误结束事件流

### Requirement: 保存并查询分析历史
系统 SHALL 将分析状态、报告和可重放时间线保存到 PostgreSQL，并通过分析 ID 查询历史记录。

#### Scenario: 查询已完成分析
- **WHEN** 客户端请求一个已经保存的分析 ID
- **THEN** 系统返回该分析的持久化报告和状态，而无需重新调用 RPC 或 LLM

#### Scenario: 重放历史时间线
- **WHEN** 客户端订阅已保存分析的 stream 端点
- **THEN** 系统按原顺序重放该分析已持久化的事件

### Requirement: 基础设施失败不得伪装成成功
系统 MUST 区分 RPC、数据库、LLM 和内部处理错误，并返回可识别的错误类别。

#### Scenario: PostgreSQL 写入失败
- **WHEN** 分析结果无法持久化
- **THEN** 系统报告数据库错误且不得返回一个声称已保存的历史记录

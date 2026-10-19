# Spec Delta

## Purpose

定义支持 MCP 的 AI 客户端如何发现并调用 ChainLens 的只读 Ethereum 交易分析能力，同时约束传输、证据来源、确定性降级、输入校验和安全边界。

## ADDED Requirements

### Requirement: MCP 客户端发现固定的只读工具集
系统 SHALL 通过 MCP 仅暴露 `analyzeTransaction`、`getCaseFacts`、`getRiskFindings`、`resolveSelector` 和 `searchKnowledge` 五个工具；每个工具 SHALL 提供明确的输入 schema、中文用途说明和只读、非破坏性提示。

#### Scenario: 客户端列出工具
- **WHEN** 已连接的 MCP 客户端请求工具列表
- **THEN** 系统按稳定顺序返回且仅返回上述五个工具及其参数 schema

#### Scenario: 客户端查找写操作
- **WHEN** 客户端检查可用工具
- **THEN** 工具集中不存在签名、私钥读取、交易广播、资产转移或任意 RPC URL 访问能力

### Requirement: 完整分析工具返回可核验报告
`analyzeTransaction` SHALL 接受一个 Ethereum 交易哈希，复用现有分析主链路返回结构化中文分析结果；重要结论 MUST 保留 `evidence`、`ruleId`、`confidence` 和原始数据定位，且响应 SHALL 表明是否使用 LLM、是否降级以及是否成功持久化。

#### Scenario: 分析有效交易
- **WHEN** 客户端使用有效交易哈希调用 `analyzeTransaction`
- **THEN** 系统查询真实 Ethereum JSON-RPC、执行确定性规则，并返回事实、findings、报告、sources、limitations 和工具步骤

#### Scenario: LLM 不可用
- **WHEN** 客户端调用 `analyzeTransaction` 时未配置模型或模型调用失败
- **THEN** 系统仍返回确定性取证和规则报告，并标记 `llmUsed:false` 与 `degraded:true`

#### Scenario: RAG 不可用
- **WHEN** 客户端调用 `analyzeTransaction` 时知识检索关闭、未就绪或故障
- **THEN** 系统不伪造 KNOWLEDGE 来源，继续返回基于 CHAIN 证据和确定性规则的结果，并在 limitations 中说明知识增强未使用

### Requirement: 独立取证与规则工具保持确定性
`getCaseFacts` SHALL 只返回指定交易的链上取证案卷；`getRiskFindings` SHALL 对指定交易重新取得事实并只返回确定性规则结果。链上事实 MUST 来自配置的 Ethereum JSON-RPC，规则结果 MUST 由规则引擎产生。

#### Scenario: 单独获取交易案卷
- **WHEN** 客户端以有效交易哈希调用 `getCaseFacts`
- **THEN** 系统返回 transaction、receipt、block、ETH 变化、已解析事件、calldata 解析和原始数据定位，不调用 LLM 生成事实

#### Scenario: 单独获取风险结论
- **WHEN** 客户端以有效交易哈希调用 `getRiskFindings`
- **THEN** 系统返回规则引擎实际命中的 findings，每项包含 `ruleId`、severity、confidence 和 evidence，不允许模型或知识库新增 finding

### Requirement: selector 与知识工具返回有界结果
`resolveSelector` SHALL 接受一个带 `0x` 前缀的 4-byte selector 并返回零个或多个候选函数签名；`searchKnowledge` SHALL 复用受控知识库契约，返回可用状态、原因以及最多三个带来源的命中。

#### Scenario: selector 存在碰撞
- **WHEN** 客户端调用 `resolveSelector` 且本地登记表存在多个候选
- **THEN** 系统返回全部候选并明确其为候选集合，不声称唯一解析结果

#### Scenario: 知识检索成功
- **WHEN** 客户端使用具体规则编号或风险名称调用 `searchKnowledge`
- **THEN** 系统返回最多三个包含来源文件、标题、关联 ruleId 和相似度的受控知识片段，并且这些片段不能作为当前交易事实证据

#### Scenario: 知识库不可用
- **WHEN** 客户端调用 `searchKnowledge` 而知识库关闭、未就绪或故障
- **THEN** 系统返回明确的不可用状态和可诊断原因，不把故障伪装为空命中

### Requirement: 支持 Streamable HTTP 与 stdio 传输
系统 SHALL 以 `/mcp` Streamable HTTP 作为主要传输，并 SHALL 提供使用同一工具实现的 stdio 运行模式供 DeepSeek Harness 本地启动；旧版 HTTP+SSE 传输不得作为 M8 的新接入方式。

#### Scenario: Streamable HTTP 客户端连接
- **WHEN** MCP 客户端连接本机 `/mcp` 并完成协议初始化
- **THEN** 客户端可以列出并调用五个工具，HTTP 端点与现有业务 SSE 端点互不冲突

#### Scenario: DeepSeek Harness 通过 stdio 启动
- **WHEN** DeepSeek Harness 使用仓库提供的本地配置启动 ChainLens MCP 子进程
- **THEN** 客户端通过标准输入输出完成协议通信，协议输出不被应用日志污染，并发现与 HTTP 模式相同的五个工具

### Requirement: MCP 输入和失败结果可诊断且不伪造
系统 MUST 在调用 RPC、LLM 或知识库前校验交易哈希、selector 和知识查询；系统 MUST 将无效输入、交易不存在、RPC 不可用、并发超限、知识不可用和内部错误区分为稳定的错误类别，且失败时不得返回伪造的成功结果。

#### Scenario: 交易哈希格式错误
- **WHEN** 客户端向需要交易哈希的工具传入非 `0x` 加 64 位十六进制值
- **THEN** 系统返回客户端输入错误且不调用 Ethereum RPC

#### Scenario: selector 格式错误
- **WHEN** 客户端向 `resolveSelector` 传入非 `0x` 加 8 位十六进制值
- **THEN** 系统返回客户端输入错误且不执行候选查询

#### Scenario: RPC 不可用
- **WHEN** 交易工具在重试后仍无法访问配置的 Ethereum RPC
- **THEN** 系统返回可识别的 `RPC_UNAVAILABLE` 错误，不编造事实、findings 或报告

### Requirement: MCP 端点默认保持本地与可观测
系统 SHALL 默认把网络服务绑定到回环地址；Streamable HTTP 在配置访问令牌时 MUST 拒绝缺失或错误令牌的请求。每次 MCP 工具调用 SHALL 记录工具名、成功状态和耗时指标，但日志与指标不得包含密钥、完整知识正文或不必要的原始链上响应。

#### Scenario: 默认本地启动
- **WHEN** 用户未设置监听地址覆盖项启动应用
- **THEN** `/mcp` 仅能通过本机回环地址访问，Docker Compose 可通过显式配置改为容器内所需监听地址

#### Scenario: 配置访问令牌
- **WHEN** 已配置 MCP HTTP 访问令牌且请求未携带正确凭据
- **THEN** 系统在执行工具前拒绝请求且不在响应或日志中泄露令牌

#### Scenario: 工具调用完成
- **WHEN** 任一 MCP 工具成功或失败
- **THEN** 系统增加带工具名和结果状态标签的调用指标，并记录不含秘密的诊断信息

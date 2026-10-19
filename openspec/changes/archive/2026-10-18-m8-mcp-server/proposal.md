# Proposal

## Why

ChainLens 已经通过 REST、SSE 和 Web 页面提供交易分析，但支持 MCP 的 AI 客户端还不能发现并安全调用这些能力。M8 将现有只读分析能力包装为标准 MCP 工具，使 DeepSeek Harness 等客户端可以在不复制业务逻辑、不放宽安全边界的前提下完成带证据的 Ethereum 交易分析。

## What Changes

- 新增 MCP Server 能力，以 Streamable HTTP `/mcp` 为主传输，并保留 stdio 作为 DeepSeek Harness 的本地接入方式。
- 对外暴露五个只读业务工具：`analyzeTransaction`、`getCaseFacts`、`getRiskFindings`、`resolveSelector`、`searchKnowledge`。
- 五个工具复用现有分析、取证、规则和知识检索服务；不从 `AgentTools` 复制依赖单次分析上下文的内部实现。
- 为 MCP 参数提供与 REST 一致的交易哈希、selector 和知识查询校验，并返回稳定、结构化、可诊断的成功或错误结果。
- 为五个工具声明只读、非破坏性提示；链上事实只来自 Ethereum JSON-RPC，风险结论只来自确定性规则，知识片段只用于解释。
- Streamable HTTP 默认只监听回环地址；不接受调用者传入 RPC URL，也不增加签名、私钥、广播交易或写链能力。
- 增加 Streamable HTTP 自动化契约测试、stdio 协议测试、MCP Inspector/客户端验证步骤，以及 DeepSeek Harness 本地配置、Cursor HTTP 配置样例和真实接入截图说明。
- Spring AI 依赖仅在验证兼容性确有需要时从 2.0.0 更新到当前稳定的 2.0.x 补丁版本；不采用开发版或里程碑版本。

**范围内**：五个只读工具、Streamable HTTP、stdio、工具 schema、参数校验、错误映射、指标、DeepSeek Harness/Cursor README 配置与本地客户端验收。

**范围外**：OAuth、公网部署、多租户授权、MCP resources/prompts、远程客户端 Connector 部署、写链工具、钱包与密钥管理、BTC、多 Agent，以及 M9 的完整评测和发布材料。

## Capabilities

### New Capabilities

- `mcp-analysis-tools`: 定义 MCP 客户端如何发现和调用五个只读分析工具，以及 Streamable HTTP/stdio 传输、安全、错误和可观测性契约。

### Modified Capabilities

无。M8 复用现有分析、规则和 RAG 行为，不修改它们已经批准的需求契约。

## Impact

- 主要影响 `chainlens-mcp`、`chainlens-app`、Maven 依赖与应用配置；现有 REST/SSE/Web 接口保持兼容。
- MCP 工具外壳将依赖稳定的应用服务接口，避免依赖只在一次 LLM 工具循环中存在的 `AgentTools` 状态。
- Streamable HTTP 使用现有 Spring MVC 进程和 `/mcp` 端点；stdio 使用独立运行配置，二者共享相同工具 bean 和业务服务。
- `analyzeTransaction` 继续遵守现有确定性降级：LLM 或 RAG 不可用时仍返回取证、规则和模板报告，并明确标记 `llmUsed:false`、`degraded:true`；其余确定性工具不依赖 LLM。
- 所有重要结论继续携带 `evidence`、`ruleId`、`confidence` 和原始数据定位；MCP 不允许模型或知识库生成新的链上事实。
- 本地运行面向 Apple Silicon/arm64，不新增平台相关二进制；Java 21、现有 PostgreSQL/pgvector 与 Anvil 运行方式保持不变。

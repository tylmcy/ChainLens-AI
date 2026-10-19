# Design

## Context

参见 [proposal.md](proposal.md) 的动机。当前系统已经有完整的 `AnalysisService`、`TxFactsProvider`、`RuleEngine`、`SelectorRegistry` 和 `KnowledgeSearch` 能力，但 `chainlens-mcp` 仍是仅含占位说明的模块。内部 `AgentTools` 绑定一次分析已经取得的 `TxFacts`，方法也没有 `txHash` 参数，不能直接作为面向多个 MCP 客户端的公共工具。

项目当前使用 Spring AI 2.0.0。2026-10-17 核对官方文档后，当前稳定版为 2.0.1：WebMVC starter 通过 `spring.ai.mcp.server.protocol=STREAMABLE` 提供 `/mcp` Streamable HTTP，并可通过 `spring.ai.mcp.server.stdio=true` 启用 stdio；旧 HTTP+SSE 已弃用。官方同时说明 MCP HTTP starter 不自带认证，暴露到 localhost 之外必须由应用负责安全层。

DeepSeek Harness 是 M8 的本地 stdio 验收客户端；项目明确不在 M8 部署公网资源。因此本地验收拆为两个互补部分：Streamable HTTP 由自动化 MCP 客户端和 MCP Inspector 验证，DeepSeek Harness 使用 stdio 启动同一个本地 jar。两种模式暴露完全相同的五个工具。

## Goals / Non-Goals

**Goals:**

- 在现有模块化单体进程内提供稳定的 `/mcp` Streamable HTTP 端点。
- 用独立 stdio profile 支持 DeepSeek Harness 本地启动，并保证 stdout 只承载协议消息。
- 暴露五个无隐藏会话状态、参数明确、输出可追溯的只读工具。
- 复用已经测试过的取证、规则、Agent 和 RAG 端口；保留无 LLM、无 RAG 时的确定性行为。
- 让 HTTP 与 stdio 共用一套工具实现、校验、错误映射和指标。

**Non-Goals:**

- 不实现 MCP resources、prompts、sampling、elicitation 或服务端 UI。
- 不为 DeepSeek Harness 远程 Connector 建公网隧道、域名、OAuth 或部署环境。
- 不把 MCP 拆成独立微服务，不复制 Ethereum RPC、规则或 RAG 实现。
- MCP 调用不写入分析历史。这样五个工具可真实声明为只读，重复调用不会新增数据库记录；REST/SSE 仍负责“分析并保存历史”的产品流程。
- 不采用 Spring AI 2.1 开发版或 MCP 2026-07-28 草案专属能力；以当前稳定依赖支持的协议为准。

## Decisions

### 1. 使用独立公共 MCP 工具类，不直接暴露 `AgentTools`

在 `chainlens-mcp` 新增无状态的公共工具类，每个交易相关方法都显式接收 `txHash`。它通过构造参数依赖 `AnalysisService`、`TxFactsProvider`、`RuleEngine`、`SelectorRegistry` 和 `KnowledgeSearch`，由 `chainlens-app` 统一装配。

三个会触发交易取证的工具还复用 `chainlens-app` 已有的同一个并发 `Semaphore`：调用前尝试取得 permit，完成或失败后在 `finally` 释放。这样 REST、SSE 与 MCP 共用总并发预算，MCP 不能绕过现有资源保护；纯本地 selector 和轻量知识查询不占用交易分析 permit。

选择该方案的原因：

- `AgentTools` 是单次 Agent 循环的内部工具，持有特定交易事实、调用步骤记录器和知识来源白名单；把它注册成单例 MCP bean 会发生串案或并发污染。
- 公共 MCP 工具必须跨连接、跨调用保持无状态，才能安全支持 Streamable HTTP 和 stdio。
- 工具只编排已有端口，不自行解析 RPC JSON、不重写规则。

备选方案是把 `AgentTools` 改造成接收 `txHash` 的全局工具，但会破坏 M5 已验证的护栏模型，并让内部 Agent 与公共协议耦合，因此不采用。

### 2. 固定暴露五个业务级工具

| 工具 | 输入 | 输出重点 | 是否访问外部能力 |
|---|---|---|---|
| `analyzeTransaction` | `txHash` | 完整 `AnalysisResult`，MCP 模式 `persisted=false` | RPC，可选 LLM/RAG |
| `getCaseFacts` | `txHash` | `TxFacts` 与原始定位 | RPC |
| `getRiskFindings` | `txHash` | network、txHash、`Finding[]` | RPC + 本地规则 |
| `resolveSelector` | `selector` | selector 与全部候选签名 | 否 |
| `searchKnowledge` | `query` | availability、reason、top-3 hits | 本地 pgvector/嵌入 |

不暴露 `getBlock`、`getContractCode` 或底层 `eth_*` 方法。前两项已经包含在取证案卷内；暴露任意底层 RPC 会扩大 SSRF、滥用和提示注入面，也会让客户端绕过证据模型。

### 3. 使用 MCP 专用注解和有类型输出

公共方法使用 Spring AI 2.0.1 的 `@McpTool` / `@McpToolParam`，开启输出 schema，并声明 `readOnlyHint=true`、`destructiveHint=false`。链上查询类工具标记会访问外部系统，纯本地 selector 工具标记为封闭域。

方法返回领域 record 或小型 MCP DTO，不返回手工拼接 JSON 字符串。这样 DeepSeek Harness 可以从 schema 理解字段，Java 编译器也能约束重构。框架负责把未捕获异常标记为 MCP tool error；工具边界先把异常映射为不含秘密的稳定类别与中文消息。

备选方案是沿用内部 `AgentTools` 的 JSON 字符串返回值，实现快但客户端只能得到 `string` schema，失去结构化输出价值，因此不采用。

### 4. Streamable HTTP 使用 WebMVC 同进程端点

`chainlens-mcp` 引入稳定版 `spring-ai-starter-mcp-server-webmvc`，配置同步服务器、`STREAMABLE` 协议和 `/mcp` 端点。现有应用已经使用 Spring MVC，继续使用 WebMVC 可避免再引入 WebFlux 运行时。

HTTP 模式是默认模式，和 REST、业务 SSE、Actuator 共用同一 Boot 进程。MCP 的 Streamable HTTP 即使使用 SSE 编码承载响应，也与 `/api/v1/analyses` 的业务进度 SSE 是两套独立协议和端点。

Spring AI 从 2.0.0 升级到当前稳定补丁 2.0.1，升级后必须先跑完整回归；若现有 DeepSeek、RAG 或 Boot 4.1 兼容性测试失败，则先解决兼容问题，不能退到 SNAPSHOT。

### 5. stdio 使用独立 profile，共用同一个可执行 jar

新增 `mcp-stdio` profile：关闭 Web Server、启用 MCP stdio，并使用专用 Logback 配置把日志写到 stderr。DeepSeek Harness 配置调用仓库脚本，脚本只负责执行已构建的 `chainlens-app` jar 和选择 profile，不安装依赖、不读取或回显密钥。

不在默认 HTTP 进程里同时开启 stdio，避免 HTTP 端口占用、生命周期混淆和 stdout 日志污染。两种运行模式通过 Spring profile 选择，但扫描同一个 MCP 工具 bean，因此工具列表和 schema 必须由测试证明一致。

DeepSeek Harness 的本地 stdio 配置只记录命令、参数和必要的非秘密环境变量示例；真实 Key 继续由用户环境或未纳入 Git 的 `.env` 提供。

### 6. 所有公共调用无 MCP 会话业务状态

工具不把“当前交易”保存在 HTTP session、MCP session、静态变量或 ThreadLocal 中。`getRiskFindings` 会根据自己的 `txHash` 重新取证再执行规则，调用成本高于缓存方案，但行为透明，不会因客户端调用顺序或重连而串案。

M8 不引入跨工具缓存。未来若性能数据证明需要缓存，应以 chainId + txHash + blockHash 为键并另开设计变更，不能使用隐式“最近一次交易”。

### 7. 参数校验和错误映射位于共享的 MCP 边界

交易哈希严格匹配 `0x` + 64 位十六进制；selector 严格匹配 `0x` + 8 位十六进制；知识查询去除首尾空白后必须非空并限制长度。校验失败时不调用下游。

MCP 错误类别复用现有语义：`INVALID_INPUT`、`TRANSACTION_NOT_FOUND`、`RPC_UNAVAILABLE`、`TOO_MANY_REQUESTS`、`KNOWLEDGE_UNAVAILABLE` 和 `INTERNAL_ERROR`。错误只返回类别、可行动的中文消息和 traceId；不返回堆栈、RPC 凭据或模型 Key。

交易工具拿不到共享并发 permit 时立即返回 `TOO_MANY_REQUESTS`，不排队占用 MCP 请求线程。permit 必须在 `finally` 中释放，并用成功、下游异常和取消三条测试路径守护，避免一次失败永久降低容量。

### 8. 本地优先安全策略

应用默认增加 `server.address=${SERVER_ADDRESS:127.0.0.1}`。Docker Compose 显式设置 `SERVER_ADDRESS=0.0.0.0`，因为容器内回环无法通过宿主端口映射访问；这不是公网授权，宿主暴露范围仍由 Compose 端口和防火墙决定。

Streamable HTTP 增加只匹配 `/mcp` 的轻量过滤器：未配置 `CHAINLENS_MCP_TOKEN` 时允许本机开发；配置后要求 Bearer Token，并使用常量时间比较。公网部署仍属于范围外，不能把这个开发级 token 描述为 OAuth 替代品。

调用者不能传 RPC URL、知识文件路径或模型配置。工具只有只读分析参数，因此从能力层面不存在签名和广播入口。

### 9. 指标与诊断

每次 MCP 工具调用记录计数和耗时，例如 `chainlens.mcp.tool.calls` 与 `chainlens.mcp.tool.duration`，标签限定为工具名、transport 和 success/failure，避免高基数 txHash 标签。日志记录 traceId、工具名、耗时和错误类别，不记录 token、API Key、完整知识正文或完整原始 RPC JSON。

### 10. 验证分为协议、业务和真实客户端三层

- 单元测试：五个工具的参数校验、DTO、错误映射、确定性来源和无状态并发。
- Spring 集成测试：用 MCP 客户端连接随机端口 `/mcp`，验证 `tools/list` 恰好五项、schema、调用结果、token 拒绝和现有 REST 共存。
- stdio 子进程测试：启动可执行 jar，发送初始化、`tools/list` 和至少一次工具调用，验证 stdout 没有日志污染。
- 全量回归：`mvn -B verify`，包括现有 DB/Testcontainers 测试；前端无变更时不强制重新构建截图批次，但需确认静态页面仍能启动。
- 用户可见验收：MCP Inspector 连接 Streamable HTTP 并完成一笔 Anvil 交易；DeepSeek Harness 通过 stdio 调用 `analyzeTransaction`，截图展示工具调用和包含 evidence/ruleId/confidence 的结果。

### 11. Apple Silicon 与资源影响

M8 只增加 Java 依赖和配置，不增加 x86 原生二进制或新容器镜像，在 MacBook Air M5/arm64 上继续使用现有 Java 21、PostgreSQL/pgvector 和 Anvil。HTTP 模式与现有应用同进程，只增加少量 MCP 会话与 schema 元数据；stdio 模式每个 DeepSeek Harness 配置启动一个 JVM，因此 README 要提示不要同时重复启动多个实例，并保留现有并发上限。

## Risks / Trade-offs

- [Spring AI 2.0.1 与现有 Boot 4.1/DeepSeek/RAG 组合出现回归] → 先做依赖兼容批次并跑全量测试；只使用稳定补丁版本，发现问题时在实现前更新设计或缩小升级面。
- [DeepSeek Harness 不需要远程 Connector] → HTTP 用标准 MCP 客户端与 Inspector 验证；DeepSeek Harness 本地验收使用 stdio，不创建公网隧道。
- [stdio stdout 被日志或启动横幅污染] → 专用 profile 禁用 banner、日志定向 stderr，并用真实子进程协议测试守护。
- [同一交易被多个工具重复取证，增加 RPC 延迟] → M8 优先保证无状态和不串案；工具描述建议优先调用 `analyzeTransaction`，仅在需要拆分证据时调用细粒度工具。
- [可选 Token 被误认为生产级安全] → README 明确只用于本地/受信网络；非回环部署必须另行设计 TLS、OAuth 和授权。
- [MCP 工具返回过大的原始数据] → 复用现有有界案卷结构并设置 MCP 请求超时；若实际超限，先通过测试定位字段，不静默截断 evidence。

## Migration Plan

1. 将 Spring AI 更新到已核实的稳定 2.0.1，跑现有完整测试作为兼容闸门。
2. 实现并单测无状态五工具适配器，再接入 Streamable HTTP。
3. 增加回环绑定、可选 Token、指标与 HTTP MCP 集成测试；确认 REST/SSE 回归。
4. 增加 stdio profile、stderr 日志配置和启动脚本，完成子进程测试。
5. 用 Anvil、MCP Inspector 和 DeepSeek Harness 进行用户可见验收，补 README 配置和截图说明。

回滚时可移除 MCP starter、工具 bean 和 MCP profiles，恢复 Spring AI 版本；现有数据库 schema、REST API 和历史数据无需迁移或回滚。

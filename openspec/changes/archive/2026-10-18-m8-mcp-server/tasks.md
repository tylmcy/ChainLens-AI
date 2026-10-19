# Tasks

## 1. 高能力批次 A：依赖与协议兼容闸门（`gpt-5.6-sol` 中等）

- [x] 1.1 将 Spring AI BOM 从 2.0.0 更新到官方当前稳定补丁 2.0.1，引入 WebMVC MCP Server starter；用 `mvn dependency:tree` 确认没有 SNAPSHOT、重复 MCP SDK 或 WebFlux 冲突
- [x] 1.2 在尚未增加业务实现前运行 `mvn -B verify`，确认 DeepSeek 工具调用、RAG、REST/SSE、DB/Testcontainers 基线在依赖升级后全部通过；若失败，记录并修复兼容原因后重新验证
- [x] 1.3 建立 MCP 最小 Spring context 冒烟测试，确认 `STREAMABLE` 配置可在 Spring Boot 4.1 与 Apple Silicon Java 21 环境启动并生成 `/mcp` handler

## 2. 高能力批次 A：五个公共工具与契约（`gpt-5.6-sol` 中等）

- [x] 2.1 在 `chainlens-mcp` 定义有类型的输出 DTO、输入校验器和稳定错误分类，并用单元测试证明非法 txHash、selector、空白/超长 query 在调用下游前被拒绝
- [x] 2.2 实现无状态 `analyzeTransaction` 与 `getCaseFacts` 工具，复用 `AnalysisService`/`TxFactsProvider`；用固定事实测试验证完整报告保留 evidence、ruleId、confidence、原始定位以及 `persisted=false`
- [x] 2.3 实现无状态 `getRiskFindings`，重新取证后只调用 `RuleEngine`；用正反例测试证明结果不依赖 LLM/RAG 且不会新增规则结论
- [x] 2.4 实现 `resolveSelector` 与 `searchKnowledge`，用碰撞 selector、空候选、知识命中、空命中和不可用状态测试验证候选集合及来源边界
- [x] 2.5 使用 `@McpTool`/`@McpToolParam` 注册且仅注册五个工具，启用输出 schema 和只读提示；用工具发现测试验证稳定顺序、精确名称、参数 schema 和不存在写链能力
- [x] 2.6 增加并发无状态测试，同时分析两个不同 txHash，验证不存在静态、ThreadLocal 或 MCP session 的“当前交易”串案
- [x] 2.7 将 `SelectorRegistry` 等已有端口改为由 `chainlens-app` 显式装配，并让三个交易工具与 REST/SSE 复用同一个并发 Semaphore；用成功、异常和取消测试验证 permit 必定释放且没有复制解析或规则实现

## 3. 高能力批次 A：Streamable HTTP、安全与可观测性（`gpt-5.6-sol` 中等）

- [x] 3.1 配置同步 Streamable HTTP `/mcp` 作为默认 MCP 传输，并用真实 MCP Java 客户端集成测试完成 initialize、tools/list 和至少一次 tools/call
- [x] 3.2 设置默认 `SERVER_ADDRESS=127.0.0.1`，在 Docker Compose 显式使用 `0.0.0.0`；分别用本机启动和容器健康检查验证 REST、业务 SSE 与 `/mcp` 都可按预期访问
- [x] 3.3 为 `/mcp` 实现可选 Bearer Token 过滤和常量时间比较；用缺失、错误、正确 Token 以及未配置 Token 四组测试验证工具执行前的拒绝行为
- [x] 3.4 将 MCP 异常映射为 `INVALID_INPUT`、`TRANSACTION_NOT_FOUND`、`RPC_UNAVAILABLE`、`TOO_MANY_REQUESTS`、`KNOWLEDGE_UNAVAILABLE`、`INTERNAL_ERROR`，用集成测试验证错误不伪装成成功且不暴露堆栈或秘密
- [x] 3.5 增加低基数工具调用计数与耗时指标，用 MeterRegistry 测试验证标签仅包含 tool、transport、status，且不包含 txHash、Token、完整知识正文或 RPC JSON
- [x] 3.6 用现有 WireMock RPC fixtures 经 `/mcp` 调用五个工具，验证事实和规则结果与直接调用现有服务一致，并验证 REST/SSE 回归不受 MCP starter 影响

## 4. 高能力批次 A：DeepSeek Harness stdio（`gpt-5.6-sol` 中等）

- [x] 4.1 新增 `mcp-stdio` profile，关闭 Web Server 与启动 banner、启用 stdio、把日志定向 stderr；用进程级测试证明 stdout 只包含合法 MCP 帧
- [x] 4.2 新增不安装依赖、不输出秘密的本地 stdio 启动脚本，并在脚本测试中验证 Apple Silicon 路径含空格时仍能定位并启动 `chainlens-app` jar
- [x] 4.3 通过 stdio 子进程完成 initialize、tools/list 和一次固定 Anvil/WireMock 工具调用，验证五个工具名称及 schema 与 Streamable HTTP 完全一致
- [x] 4.4 验证 stdio 子进程收到 EOF 或 DeepSeek Harness 关闭连接后能正常退出，不遗留 JVM、端口监听或临时秘密文件

## 5. 低能力连续批次 B：文档、固定配置与截图材料（`gpt-5.6-luna` 中等）

- [x] 5.1 在 README 增加 Streamable HTTP 启动、MCP Inspector 与 Cursor HTTP 配置、五个工具示例和常见错误排查，并逐条执行文档命令验证可复制
- [x] 5.2 增加 DeepSeek Harness stdio 配置样例，使用占位绝对路径且不写入 API Key；按样例完成一次本地启动验证
- [x] 5.3 更新 `.env.example` 与 Docker Compose 配置说明，加入 `SERVER_ADDRESS` 和可选 `CHAINLENS_MCP_TOKEN`，并用秘密扫描或 `rg` 验证仓库没有真实 Token/Key
- [x] 5.4 生成固定 Anvil 演示步骤和脱敏示例输出，验证结果包含 evidence、ruleId、confidence、CHAIN/KNOWLEDGE sources 及明确降级标记
- [x] 5.5 保存 MCP Inspector 的 Streamable HTTP 截图，以及 DeepSeek Harness 通过 stdio 调用 `analyzeTransaction` 的截图；人工检查截图不含 API Key、Token、用户名私密路径或无关窗口内容（`docs/screenshots/m8-inspector-tools.png`、`docs/screenshots/m8-inspector-analysis.png`、`docs/screenshots/m8-deepseek-harness-stdio.png`）

## 6. 高能力集中审查批次 C（`gpt-5.6-sol` 中等）

- [x] 6.1 运行完整 `mvn -B verify`（包含 DB/Testcontainers），并单独运行 MCP HTTP 与 stdio 集成测试；保存测试数量、耗时和失败为零的可复核结果
- [x] 6.2 在 Anvil 演示环境分别通过 MCP Inspector/HTTP 和 DeepSeek Harness/stdio 分析同一交易，对比 `txHash`、`network`、`findings`、`evidence` 和 `ruleId` 一致，确认 HTTP 主传输与本地 stdio 验收客户端均通过（2026-10-18 已用同一 Anvil 交易在运行中的 GUI 与独立 headless profile 实机调用 `mcp__chainlens__analyzeTransaction`，稳定字段与容器 HTTP `/mcp` 一致，见 `verification.md` 和 Harness 截图）
- [x] 6.3 集中审查依赖方向、公共 schema、异步/并发、错误边界、日志泄密和 Docker 监听范围；用 `git diff --check`、依赖树和针对性测试关闭发现的问题
- [x] 6.4 对照 `mcp-analysis-tools` 的全部 WHEN/THEN 场景逐项记录验证证据，确认没有未解决占位项、旧 HTTP+SSE、未声明第六个工具或范围外 OAuth/公网部署
- [ ] 6.5 进行学习验收：由用户根据 README 独立说明 Streamable HTTP 与业务 SSE 的区别、五个工具为何只读、DeepSeek Harness 为何本地使用 stdio，并独立复现一次连接和一次无效参数排错；仅在这些步骤完成后判定 M8 已掌握

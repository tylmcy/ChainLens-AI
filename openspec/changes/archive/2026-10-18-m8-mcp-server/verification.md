# M8 验收记录

## 验收环境与结果

- 日期：2026-10-17
- 平台：Apple Silicon MacBook Air M5
- Java：Temurin/OpenJDK 21.0.11（显式设置 `JAVA_HOME=/opt/homebrew/opt/openjdk@21`）
- Docker：Docker Desktop 29.6.1；Testcontainers 2.0.5
- 全量命令：`env JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn -B verify`
- 全量结果：10 个 Maven 模块全部成功；161 个测试，0 failure、0 error、0 skipped；24.895 秒
- HTTP 单测命令：`mvn -B -pl chainlens-mcp -am test -Dtest=McpStreamableHttpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
- HTTP 单测结果：1/1 通过；Maven 总耗时 4.764 秒
- stdio 单测命令：`mvn -B -pl chainlens-app -am test -Dtest=McpStdioProcessIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
- stdio 单测结果：1/1 通过；Maven 总耗时 5.890 秒

> 两条单测命令实际也使用上述 Java 21 `JAVA_HOME`。全量测试包含 PostgreSQL Testcontainers、WireMock RPC、真实 HTTP socket 和真实 Java stdio 子进程，不依赖真实资产或私钥。

## 2026-10-18 HTTP/stdio 等价性复验（Anvil 固定案例）

- 固定交易：`unlimited-approval` 场景，`0x924318416f8abc744d48948137ed7aee65715388a95198ff46834a4fd1a7c1e3`（Anvil `chainId=31337`，block 2）。
- 构建：`mvn -B -pl chainlens-app -am package -DskipTests`，以工作树当前代码重新生成 `chainlens-app/target/chainlens-app-0.1.0-SNAPSHOT.jar`。
- 传输 A：同一 jar 以默认 Streamable HTTP 启动于 `127.0.0.1:18080`，完成 initialize → tools/list → `tools/call analyzeTransaction`。
- 传输 B：同一 jar 以 `-Dspring.profiles.active=mcp-stdio` 启动，经 stdin/stdout 逐行 JSON-RPC 完成 initialize → tools/list → `tools/call analyzeTransaction`；关闭 stdin 后进程退出码为 0，启动日志只出现在 stderr。
- 对比结果：
  - initialize 的 `protocolVersion` 与 `serverInfo` 完全一致。
  - tools/list 的五个工具名称、顺序和输入/输出 schema 完全一致。
  - `analyzeTransaction` 的稳定字段完全一致：`txHash`、`network=ANVIL`、`report.status=SUCCESS`、`ruleId=ERC20-UNLIMITED-APPROVAL`、`severity=HIGH`、`confidence=1.0`、`evidence.rpcMethod=eth_getTransactionReceipt`、`evidence.rawJsonRef.jsonPointer=/result/logs/0`、`sources[].type=CHAIN`、`llmUsed=false`、`degraded=true`、`persisted=false`。
  - 全量结构 diff 仅剩 6 个易变字段：`analysisId`、`createdAt`、`facts.fetchedAt`、`findings[0].evidence.fetchedAt`、`report.findings[0].evidence.fetchedAt`、`totalDurationMs`；它们均为每次调用的时间戳、随机 ID 或耗时，不承载交易事实。
- 另对正在运行的容器 `/mcp`（`127.0.0.1:8080`）执行同一调用，上述稳定字段与本轮 stdio 结果一致。
- 结论：在相同代码和相同 Anvil 事实上，Streamable HTTP 主传输与 stdio 传输的协议行为和业务结果等价。随后已在运行中的 DeepSeek Harness GUI 通过 `mcp__chainlens__analyzeTransaction` 完成真实 stdio MCP 调用，结果与 HTTP 一致；脱敏画面见 `docs/screenshots/m8-deepseek-harness-stdio.png`。用户学习验收仍属于任务 6.5。

## 2026-10-18 DeepSeek Harness 实机 stdio 验收

- 配置位置：当前运行的 GUI 进程使用 `~/.dsh/profiles/desktop/cordis.patch.yml`；同时在 `~/.dsh/profiles/web/cordis.patch.yml` 写入相同条目，供 `dsh web` 复用。
- 条目语法：DSH 的 profile patch 新增插件行必须放在 `insert` 下，否则启动时只会得到 `patch: entry "mcp-chainlens" not found`：

  ```yaml
  - insert:
      - id: mcp-chainlens
        name: "@deepseek-ai/dsh-mcp-client"
        config:
          serverName: chainlens
          transport: stdio
          command: "/Users/cixu/Documents/workplace/小项目/ChainLens AI/scripts/start-mcp-stdio.sh"
          args: []
          toolCallTimeoutMs: 120000
  ```

- 生效结果：GUI 热加载后工具列表出现 `mcp__chainlens__analyzeTransaction` 等五个工具；系统中可见独立 Java stdio 子进程，未占用 8080 端口。
- GUI 实机调用：`mcp__chainlens__analyzeTransaction`（`txHash=0x924318416f8abc744d48948137ed7aee65715388a95198ff46834a4fd1a7c1e3`）返回 `network=ANVIL`、`status=SUCCESS`、`ruleId=ERC20-UNLIMITED-APPROVAL`、`severity=HIGH`、`confidence=1.0`、`rpcMethod=eth_getTransactionReceipt`、`jsonPointer=/result/logs/0`、`llmUsed=false`、`degraded=true`、`persisted=false`。
- 独立 headless 复核：临时 `m8-verify` profile 运行 `dsh m8-verify --json "<提示>"`，事件流出现 `tool_call → tool_result status=completed`，工具名同为 `mcp__chainlens__analyzeTransaction`，最终输出与 GUI 调用一致。
- 与运行中容器的 HTTP `/mcp` 结果对比：上述稳定字段一致，仍只有 `analysisId`、时间戳和耗时不同。
- 剩余：任务 5.5 的脱敏截图尚未落盘；任务 6.5 由用户完成学习验收。

## `mcp-analysis-tools` 场景对照

| Requirement / Scenario | 状态 | 可复核证据 |
| --- | --- | --- |
| 固定工具集 / 客户端列出工具 | 通过 | `McpStreamableHttpIntegrationTest#initializesDiscoversFiveToolsAndCallsOneOverStreamableHttp` 对真实 `/mcp` 执行 initialize、tools/list，并断言稳定顺序、五个名称、输入/输出 schema。 |
| 固定工具集 / 客户端查找写操作 | 通过 | `ChainLensMcpToolsTest#exposesExactlyFiveReadOnlySchemaGeneratingTools` 断言仅五个 `@McpTool`，全部 `readOnlyHint=true`、`destructiveHint=false`，且不存在 sign/send/write/broadcast 工具。 |
| 完整分析 / 分析有效交易 | 通过 | `FullStackAnalysisIntegrationTest#mcpToolsUseExistingBusinessServicesAndCoexistWithRestAndSse` 通过真实 MCP 客户端、WireMock JSON-RPC 与 PostgreSQL 调用五个工具，断言 txHash、finding、evidence/ruleId/confidence 与业务服务一致。Inspector 截图见 `docs/screenshots/m8-inspector-analysis.png`。 |
| 完整分析 / LLM 不可用 | 通过 | `FullStackAnalysisIntegrationTest#fullJsonFlow` 在 `spring.ai.model.chat=none` 下断言 `llmUsed=false`、`degraded=true`，确定性报告仍成功。MCP 视图由 `ChainLensMcpToolsTest#completeAnalysisPreservesEvidenceAndForcesMcpResultToNotPersisted` 验证。 |
| 完整分析 / RAG 不可用 | 通过 | 全链路在 embedding/知识增强关闭时仍完成；`ChainLensMcpToolsTest#knowledgeSearchPreservesHitEmptyAndUnavailableStates` 区分命中、空命中和 `NOT_READY`，不把不可用伪装为空命中。 |
| 独立取证 / 单独获取交易案卷 | 通过 | `ChainLensMcpToolsTest#caseFactsReturnsTheExactForensicRecord` 验证原始 transaction/receipt 保真；全链路测试验证数据来自 RPC fixture。 |
| 独立规则 / 单独获取风险结论 | 通过 | `ChainLensMcpToolsTest#riskFindingsOnlyComeFromTheDeterministicRuleEngine` 验证只调用取证端口与 `RuleEngine`，不调用 AnalysisService/KnowledgeSearch。 |
| selector / 存在碰撞 | 通过 | `ChainLensMcpToolsTest#selectorResolutionKeepsAllCollisionCandidatesAndReportsMisses` 保留两个候选并返回 `AMBIGUOUS`。 |
| 知识 / 检索成功 | 通过 | `ChainLensMcpToolsTest#knowledgeSearchPreservesHitEmptyAndUnavailableStates` 验证来源、标题、ruleId、相似度及最多三条的知识端口契约；工具说明明确知识不能作为交易事实。 |
| 知识 / 知识库不可用 | 通过 | 同一测试验证 `NOT_READY` 与诊断原因；`McpErrorMapperTest#knowledgePortExceptionUsesKnowledgeUnavailableWithoutLeakingCause` 验证异常映射为 `KNOWLEDGE_UNAVAILABLE` 且不泄密。 |
| 传输 / Streamable HTTP 客户端连接 | 通过 | 独立 HTTP 集成测试完成 initialize/list/call；全链路测试同时回归 REST JSON 和业务 `text/event-stream`，证明它们与 MCP `/mcp` 不冲突。 |
| 传输 / DeepSeek Harness 通过 stdio 启动 | 通过 | `McpStdioProcessIntegrationTest` 用与 DeepSeek Harness 相同的 stdio transport 启动真实 JVM，完成 initialize/list/call，验证 stdout 未被日志污染并在 EOF 后退出。2026-10-18 进一步在运行中的 GUI 与独立 headless profile 中实机调用 `mcp__chainlens__analyzeTransaction`，均得到相同的 Anvil 分析结果；脱敏截图见 `docs/screenshots/m8-deepseek-harness-stdio.png`。 |
| 输入失败 / 交易哈希格式错误 | 通过 | `ChainLensMcpToolsTest#rejectsInvalidInputsBeforeCallingAnyDownstreamPort` 断言 `INVALID_INPUT` 且所有下游零交互。 |
| 输入失败 / selector 格式错误 | 通过 | 同一单元测试验证查询登记表前拒绝；HTTP 集成测试还验证协议错误不包含输入、类名、堆栈或秘密。 |
| 输入失败 / RPC 不可用 | 通过 | `McpErrorMapperTest#mapsRpcFailuresToStableSafeCategories` 验证 `RPC_UNAVAILABLE`、retryable 与安全消息；全量测试覆盖 RPC fixture 主路径。 |
| 安全 / 默认本地启动 | 通过 | `application.yml` 默认 `SERVER_ADDRESS=127.0.0.1`；Docker Compose 仅在容器内显式设为 `0.0.0.0`，容器健康检查、REST/SSE/MCP 均已实测。 |
| 安全 / 配置访问令牌 | 通过 | `McpTokenFilterTest` 覆盖缺失、错误、正确与未配置四类请求，拒绝发生在 endpoint/tool 执行之前，响应不回显 Token。 |
| 可观测 / 工具调用完成 | 通过 | `McpMetricsTest#recordsOnlyLowCardinalityToolTransportAndStatusTags` 验证计数与耗时只使用 tool、transport、status，不含 txHash、Token、RPC JSON 或知识正文。 |

## 集中审查结论

- `git diff --check` 通过，无空白符错误。
- Maven 依赖树中 MCP 只来自 Spring AI 2.0.1 的 WebMVC starter；没有 WebFlux starter、外部 SNAPSHOT 或重复 MCP SDK。
- `chainlens-app` 只负责装配；`chainlens-mcp` 依赖既有 model/chain/rules/knowledge/agent 公共端口，没有复制 JSON-RPC 解析或规则实现。
- 仅存在五个 `@McpTool`；没有第六个工具、写链工具、任意 RPC URL 参数、私钥或助记词入口。
- MCP 使用 `/mcp` Streamable HTTP；项目中的 SSE 仅为既有业务进度端点，没有新增旧版 MCP HTTP+SSE transport。
- 默认只监听 `127.0.0.1`；Docker 内的 `0.0.0.0` 仅服务宿主端口映射。Bearer Token 是本地/受信网络轻量门禁，文档未把它描述为 OAuth 或公网安全方案。
- 并发调用用共享 Semaphore，成功、异常、取消与耗尽路径均有测试；工具不保存静态、ThreadLocal 或 session 级“当前交易”。
- 仓库扫描未发现真实 API Key、Bearer Token 或密钥值；样例均为空值或明显测试占位值。
- 审查修正了 README 中 Token 错误码与实际实现不一致的问题：实际为 `401 / UNAUTHORIZED`。

## 尚未关闭的验收项

1. 任务 6.5：由用户独立完成 `docs/M8-学习验收.md`；自动测试和客户端实机通过不等于已经掌握。

因此，M8 工程实现、自动化验收、HTTP/stdio 字段对比和客户端截图材料均已完成；OpenSpec change 仍暂不归档，直到用户完成学习验收。

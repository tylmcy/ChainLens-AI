# ChainLens AI

**基于链上证据的 Ethereum 交易分析 Agent。** 输入一笔交易的哈希，ChainLens 从 Ethereum JSON-RPC 获取交易、回执和区块，解析 ETH 变化与 ERC-20 事件，执行确定性风险规则，再生成可追溯的中文报告。

报告里的风险结论包含 `ruleId`、`confidence`、`evidence` 和原始数据位置。模型负责调用工具与组织说明；链上事实来自 RPC，风险命中由规则引擎决定。模型不可用时，解析、规则和中文降级报告仍可使用。

[查看实际示例报告](docs/M9-Anvil-示例报告.md) · [查看 30 条交易评测结果](eval/results/offline/latest.md) · [阅读系统设计](设计文档.md)

![ChainLens 在线分析与离线评测架构](docs/architecture.svg)

## 能做什么

| 能力 | 实现方式 |
| --- | --- |
| 交易取证 | 读取 transaction、receipt、block 和必要的历史合约代码；原始响应位置可回查 |
| 交易解析 | 判断成功或失败、计算 ETH 变化、解析 ERC-20 Transfer/Approval；calldata 不能唯一识别时标为 `AMBIGUOUS` 或 `UNRESOLVED` |
| 风险分析 | 七条确定性规则，包括无限额度授权、执行失败、零地址转账、大量 Token 转移和风险地址命中 |
| Agent 报告 | Spring AI 工具调用、结构化中文输出、事实一致性校验；REST、SSE 进度与历史记录 |
| 知识检索 | 可选 pgvector 中文知识库，只解释风险概念，不充当当前交易的链上证据 |
| 外部集成 | 五个只读 MCP 工具，支持 Streamable HTTP 和本地 stdio |
| 可重复评测 | 30 条冻结 RPC 用例离线运行；Full 模式显式调用模型并记录工具、事实、延迟、token 和费用 |

![交易报告中的规则依据与原始证据位置](docs/screenshots/m9-anvil-evidence.png)

## 运行本地演示

在 Apple Silicon Mac 上安装并启动 Docker Desktop，然后在仓库根目录执行：

```bash
./scripts/demo-up.sh
```

脚本启动 PostgreSQL、Anvil 本地链和应用，并生成覆盖七条规则的虚拟资产交易。看到“7 条规则均由真实 Anvil 交易触发”后：

1. 打开 <http://localhost:8080/>。
2. 从 `data/anvil-demo/transactions.json` 复制一个 `txHash` 到页面。
3. 查看 SSE 分析步骤、中文报告、风险依据和可展开的原始证据位置。

演示不使用真实资产、私钥、外部 RPC 或模型密钥。未配置模型时，页面会显示“确定性降级报告”。首次启动需要下载 Docker 镜像和 Maven 依赖；依赖可下载且 Docker Desktop 已运行时，项目提供约 10 分钟的本地演练路径。其他平台可尝试相同 Compose 流程，已记录的干净环境演练基于 Apple Silicon Mac。

端口被占用时可改用 `CHAINLENS_PORT=18080 ./scripts/demo-up.sh`。停止演示但保留数据库历史：`docker compose --profile demo-offline down`。健康检查位于 <http://localhost:8080/actuator/health>。

## 评测与已知结果

版本化评测集包含 **15 条 Anvil 构造交易 + 15 条公开 Ethereum 历史交易**。历史 RPC 响应已冻结，离线评测复用生产解析器和规则引擎，无需 Docker、数据库、外部 RPC 或模型。

| 指标 | 当前固定样本结果 | CI 门槛 |
| --- | ---: | ---: |
| 解析字段准确率 | 585 / 585 | ≥ 98% |
| 七规则分类准确率 | 210 / 210（TP 18、TN 192） | 100% |

这些数字只描述当前 30 条用例，不能推断所有 Ethereum 交易的准确率。[JSON 原始结果](eval/results/offline/latest.json)保留分子、分母和逐例差异；[数据集清单](eval/cases/transaction-eval-v1.json)列出来源与预期值。GitHub Actions 在 push 和 pull request 上执行独立离线门槛；[一次已通过的 CI 运行](https://github.com/tylmcy/ChainLens-AI/actions/runs/39192303469)包含报告 artifact。

```bash
# 需要 Java 21+ 与 Maven 3.9+；不需要启动应用
mvn -B -ntp -P transaction-eval -pl chainlens-evaluation -am test \
  -Dtest=TransactionOfflineEvaluationGateTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Full 模式显式调用真实模型，记录工具成功率、事实一致率、各阶段 P50/P95 延迟、token 用量和预算内的费用估值。一次 DeepSeek 30 条复跑为 **29/30 通过**：唯一的 `historical-2022-eth` 在报告校验后降级，因此不把这轮结果写成全绿。[工程验收记录](docs/M9-工程验收.md)说明失败与指标口径；[Ollama/DeepSeek 同交易对照](docs/M9-Ollama-对照.md)展示模型提供商可替换，但单笔对照不代表两种模型输出等价。Full 评测仅通过手动工作流或显式 `evaluation-full` profile 运行，默认 CI 不调用付费模型。

## API 与集成

- `POST /api/v1/analyses`：提交交易哈希并获取结构化报告；请求头 `Accept: text/event-stream` 可接收分析进度。
- `GET /api/v1/analyses/{id}`：读取历史报告。
- `GET /api/v1/analyses/{id}/stream`：重放历史进度。
- `/swagger-ui.html`、`/v3/api-docs`：接口文档。
- `http://127.0.0.1:8080/mcp`：MCP Streamable HTTP 端点。

MCP 暴露 `analyzeTransaction`、`getCaseFacts`、`getRiskFindings`、`resolveSelector` 和 `searchKnowledge` 五个只读工具。前两个返回报告或事实，第三个执行规则，后两个解释 selector 与受控知识；MCP 不签名、不广播交易。[MCP Inspector 演示](docs/M8-MCP-演示.md)、[Cursor HTTP 配置](docs/mcp/cursor-http.example.json)和 [DeepSeek Harness stdio 配置](docs/mcp/deepseek-harness-stdio.example.json)提供可复现调用。

## 配置与开发

默认演示所需服务由 Compose 提供。要分析外部历史交易，可通过环境变量配置只读 `ETH_RPC_URL`；启用模型时配置 `SPRING_AI_MODEL_CHAT` 和对应提供商的密钥。可选配置见 [`.env.example`](.env.example)，不要把实际密钥提交到仓库。

| 场景 | 命令或入口 |
| --- | --- |
| 完整 Java 验证 | `mvn -B -ntp verify` |
| PostgreSQL/Testcontainers 测试 | `mvn -B -ntp verify -Dgroups=db` |
| 前端开发 | `cd web && pnpm install --frozen-lockfile && pnpm dev` |
| 前端检查 | `cd web && pnpm typecheck && pnpm test && pnpm build` |
| 构建含前端的应用 | `mvn -B -ntp -pl chainlens-app -am -Pweb package` |

Java 编译目标为 21；前端开发需要 Node 22 与 pnpm 11.9.0。Docker 演示不要求宿主机安装 Java、Node、Foundry 或 PostgreSQL。项目使用 Spring Boot 4.1、Spring AI 2.0、PostgreSQL + pgvector、Anvil、Micrometer 和 Testcontainers。

RAG 默认关闭，演示启动和常规测试不下载嵌入模型。需要本地知识检索时，先启动 Compose 中的 PostgreSQL，再以 `rag-local` profile、`SPRING_AI_MODEL_EMBEDDING=transformers` 启动应用；首次运行会下载中文嵌入模型。源码、模块边界和设计取舍见 [设计文档.md](设计文档.md)。

## 边界与故障排查

ChainLens 当前只分析单笔 Ethereum 交易。它不判断地址背后的真实身份，不审计合约，不追踪钱包的完整历史，也不进行交易签名或自动交易。RAG 文档只提供背景解释，不能覆盖 JSON-RPC 事实或增加风险 finding。报告基于公开链上数据自动生成，**不构成投资建议或完整安全审计**。

- 页面打不开：检查 `docker compose --profile demo-offline ps` 与 <http://localhost:8080/actuator/health>；若改过 `CHAINLENS_PORT`，使用对应端口。
- `TRANSACTION_NOT_FOUND`：确认交易哈希属于当前连接的网络；本地演示哈希只在本次 Anvil 链上有效。
- `RPC_UNAVAILABLE`：检查 Anvil 或外部只读 RPC；系统会返回错误，不会补造交易事实。
- 模型不可用：仍返回带 `llmUsed:false`、`degraded:true` 的确定性分析。
- RAG 不可用：交易解析和规则分析继续工作，知识检索会明确标注不可用。
- MCP 返回 `401`：已设置 `CHAINLENS_MCP_TOKEN` 时，客户端需发送匹配的 Bearer Token。

## 项目资料

[架构图源文件](docs/architecture.mmd) · [Web 页面截图](docs/screenshots/m6-desktop-start.png) · [MCP 工具截图](docs/screenshots/m8-inspector-tools.png) · [示例报告](docs/M9-Anvil-示例报告.md) · [M9 OpenSpec 归档](openspec/changes/archive/2026-10-19-m9-evaluation-release/) · [学习验收清单](docs/M9-学习验收.md)

Apache-2.0，见 [LICENSE](LICENSE)。

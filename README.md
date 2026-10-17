# ChainLens AI

基于证据的 Ethereum 链上交易分析 Agent：输入交易哈希，从 Ethereum JSON-RPC 取证，用确定性规则引擎判定风险，由 AI 生成带证据（evidence）、规则编号（ruleId）、置信度（confidence）的中文结构化分析报告。

> ⚠️ 本工具基于公开链上数据自动生成分析报告，**不构成投资建议，也不是完整的安全审计**。

## 当前状态

🚧 **开发中**。M0～M6 的工程实现与自动化验收已完成；M7 RAG 知识库正在验收，学习验收仍需由使用者亲自完成。现有能力包括 Ethereum JSON-RPC 取证与解析、7 条确定性风险规则、REST/SSE、中文降级报告、PostgreSQL 历史记录、OpenAPI、Anvil 本地演示、Agent 工具调用编排、受控中文知识库，以及 Web 演示页。无 LLM Key 时返回 `llmUsed:false`、`degraded:true`，确定性链路仍可工作。设计与里程碑见 [设计文档.md](设计文档.md)。

## M7 RAG 知识库

大白话说，RAG 是“先从项目自己的中文说明书里找最相关的三段，再让 AI 用这些段落解释风险”。它不是链上取证：交易状态、地址、金额、finding、ruleId、confidence 和 evidence 仍只能来自 JSON-RPC 与确定性规则。报告中的 `CHAIN` 来源证明当前交易事实，`KNOWLEDGE` 来源只解释概念，而且只能引用本次 `searchKnowledge` 工具实际返回过的文档片段。

RAG 默认关闭，所以普通 `mvn verify`、`demo-offline` 和十分钟演示不会下载模型。关闭或故障时，`NoopKnowledgeSearch` 会明确返回不可用，交易解析和规则报告继续工作。

本地启用步骤：

```bash
# 1. 启动 pgvector（也可复用已运行的 demo-offline PostgreSQL）
docker compose --profile demo-offline up -d postgres

# 2. 首次运行会下载约 95 MB 的 BGE-small-zh-v1.5 单文件 ONNX 模型
#    以及 DJL 在 macOS arm64 上进行张量计算所需的一次性 CPU 原生库
SPRING_PROFILES_ACTIVE=rag-local \
SPRING_AI_MODEL_EMBEDDING=transformers \
CHAINLENS_KNOWLEDGE_ADMIN_ENABLED=true \
mvn -pl chainlens-app -am spring-boot:run

# 3. 可选：从本机手动触发一次固定知识目录同步；端点不接受路径或 URL
curl -X POST http://127.0.0.1:8080/api/v1/knowledge/sync
```

默认模型缓存目录是 `.cache/chainlens/onnx`，可用 `CHAINLENS_EMBEDDING_CACHE_DIR` 改到持久目录；DJL 原生库另外缓存在用户目录的 `.djl.ai`。这些缓存和模型二进制都不会提交 Git。模型与 tokenizer 地址也可分别用 `CHAINLENS_EMBEDDING_MODEL_URI`、`CHAINLENS_EMBEDDING_TOKENIZER_URI` 覆盖，包括指向已下载文件的 `file:/absolute/path/model.onnx`。管理端点默认不存在，只有显式设置 `CHAINLENS_KNOWLEDGE_ADMIN_ENABLED=true` 后才注册，并且只接受 loopback 请求。

真实检索评测使用 12 条版本化中文问句，其中 10 条有目标来源、2 条无关查询：

```bash
CHAINLENS_EMBEDDING_CACHE_DIR=.cache/chainlens/onnx \
mvn -P rag-eval -pl chainlens-evaluation -am test \
  -Dtest=RagRetrievalEvaluationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果写入 `eval/results/rag/`，当前锁定阈值 `0.50` 的本地真实结果是 10/10 定向问句 Top-3 命中，2 条无关查询均被过滤。若模型下载卡住或留下 0 字节缓存，先检查 Hugging Face 大文件访问，必要时用 `curl -L` 手动下载后通过上述 `file:` URI 覆盖，而不是先怀疑 Docker；若报 `publish.djl.ai` 错误，是首次下载 DJL `osx-aarch64` CPU 原生库未完成；若报 pgvector 表或扩展错误，确认镜像是项目固定的 `pgvector/pgvector:0.8.6-pg17-bookworm`；若搜索返回 `NOT_READY`，表示启动后的后台同步尚未完成。学习时请完成 [M7 学习验收清单](docs/M7-学习验收.md)，不要只以测试通过判断自己已经掌握。

## M6 Web 演示页

页面入口：启动本地演示后访问 <http://localhost:8080/>。输入 `data/anvil-demo/transactions.json` 中任意 `txHash`，可查看 SSE 分析步骤、交易摘要、风险规则、证据的 RPC 方法和 JSON Pointer，以及最近分析。浏览器只保存最近 10 条分析 ID 索引；完整报告保存在 PostgreSQL。页面会明确标出 LLM 未启用时的“确定性降级报告”。

已用真实 Anvil 交易在浏览器走通“输入 → SSE 时间线 → 报告 → 展开原始证据 → 历史恢复”；桌面 1280px 与窄屏 390px 下均无横向溢出。以下截图展示初始页面，报告请用本地演示数据亲自生成。

![ChainLens 桌面首页](docs/screenshots/m6-desktop-start.png)

![ChainLens 窄屏首页](docs/screenshots/m6-mobile-start.png)

本地开发需要 Java 21、Node 22 和 pnpm 11.9.0：

```bash
# 终端 1：先启动后端及 Anvil/PostgreSQL，或使用下方 Docker 演示
./scripts/demo-up.sh
# 终端 2：前端热更新，代理 /api 到 localhost:8080
cd web
pnpm install --frozen-lockfile
pnpm dev
```

前端验证与单一 Boot 制品：

```bash
cd web && pnpm typecheck && pnpm test && pnpm build
cd ..
mvn -pl chainlens-app -am -Pweb package
```

`-Pweb` 需要宿主机有 pnpm；默认 `mvn verify` 不调用 Node。Docker 完整构建自带固定 Node 22.23.2 阶段，不要求 Mac 安装 Node。若页面无法加载，先检查 `docker compose ps` 和 `http://localhost:8080/actuator/health`；若分析返回 RPC 错误，确认 Anvil 已就绪并使用本地演示交易哈希。若历史条目失效，可在页面移除本地索引；这不会删除数据库记录。

如果你把 M6 当作学习项目，请按 [M6 学习验收清单](docs/M6-学习验收.md) 自己解释、修改、复现和排错；自动测试通过不代表这些技能已经掌握。

## M5 Agent 编排

- 设置 `SPRING_AI_MODEL_CHAT=deepseek` + `DEEPSEEK_API_KEY` 后自动启用 LLM 编排；项目会将后者映射到 Spring AI 的 `spring.ai.deepseek.api-key`（否则走确定性降级链路）；
- 工具调用步骤随 `tool_steps` 字段返回，SSE 直播 `tool.started` / `tool.completed` 事件；
- 护栏：强制取证 → 上下文装配 → 事实一致性校验 → Schema 校验+重试+降级；
- 指标：`/actuator/metrics`（如 `chainlens.tool.calls` 成功率）。

## M3 接口与验证

- `POST /api/v1/analyses`：同步 JSON；请求头 `Accept: text/event-stream` 时返回 SSE 进度。
- `GET /api/v1/analyses/{id}`：查询历史分析。
- `GET /api/v1/analyses/{id}/stream`：重放历史时间线。
- `/swagger-ui.html`、`/v3/api-docs`：OpenAPI 文档。

使用 Java 21 运行完整验证（默认排除需要真实模型或 API Key 的 `smoke` 测试）：

```bash
mvn verify
```

只运行 PostgreSQL/Testcontainers 测试：

```bash
mvn verify -Dgroups=db
```

## M4：10 分钟本地演示

你只需要安装并启动 Docker Desktop。**不需要在 Mac 上安装 Foundry、Anvil、PostgreSQL、Java 或 Node**；Compose 会使用固定版本的 ARM64 镜像，适配 Apple Silicon。

首次运行需要联网下载镜像和 Maven 依赖，之后同一版本可复用 Docker 缓存。演示运行时不访问外部 Ethereum RPC，也不需要 LLM Key。

```bash
./scripts/demo-up.sh
```

脚本会完成四件事：启动 PostgreSQL 17 + pgvector、启动内存中的 Anvil 本地链、生成覆盖 7 条规则的测试交易、启动 ChainLens 并逐条验收。成功时会看到：

```text
M4 演示验收通过：7 条规则均由真实 Anvil 交易触发。
```

如果本机 `8080` 已被其他项目占用，换一个端口即可，不用停止原项目：

```bash
CHAINLENS_PORT=18080 ./scripts/demo-up.sh
```

也可以不用封装脚本，直接启动 Compose profile：

```bash
docker compose --profile demo-offline up --build -d
./scripts/demo-verify.sh
```

### 看懂演示数据

启动后，`data/anvil-demo/transactions.json` 会列出 7 个场景的交易哈希和预期规则。这个文件是运行时生成的，所以不会提交到 Git。任选一个哈希请求 API：

```bash
TX_HASH=$(sed -n 's/.*"txHash":"\([^"]*\)".*/\1/p' data/anvil-demo/transactions.json | head -1)
curl -s -H 'Content-Type: application/json' \
  -d "{\"txHash\":\"$TX_HASH\"}" \
  http://localhost:8080/api/v1/analyses
```

- API：<http://localhost:8080/api/v1/analyses>
- Swagger：<http://localhost:8080/swagger-ui.html>
- Anvil JSON-RPC：<http://localhost:8545>
- PostgreSQL：`localhost:5432`

这里使用的是 Anvil 默认公开测试账户和虚拟 ETH，只存在于本机内存链。脚本通过节点解锁账户发送交易，仓库里没有私钥、助记词或真实资产。

停止容器并保留数据库历史：

```bash
docker compose --profile demo-offline down
```

同时清空本地演示数据库：

```bash
docker compose --profile demo-offline down -v
```

如果 Docker Desktop 注入了指向 `127.0.0.1` 的代理，Compose 已为容器间的 `anvil`、`postgres`、`chainlens-app` 通信配置 `NO_PROXY`，无需为了本项目反复修改全局代理。

### 演示数据如何生成

源码位于 `scripts/anvil-demo/src/DemoTransactions.sol`，固定字节码由官方 `ethereum/solc:0.8.30` 生成。只有修改合约源码后才需要重建字节码：

```bash
./scripts/anvil-demo/build-bytecode.sh
```

## 技术栈

Java 21 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL + pgvector · Anvil · Vite

## 许可证

Apache-2.0，见 [LICENSE](LICENSE)。

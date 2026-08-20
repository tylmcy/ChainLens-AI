# ChainLens AI

基于证据的 Ethereum 链上交易分析 Agent：输入交易哈希，从 Ethereum JSON-RPC 取证，用确定性规则引擎判定风险，由 AI 生成带证据（evidence）、规则编号（ruleId）、置信度（confidence）的中文结构化分析报告。

> ⚠️ 本工具基于公开链上数据自动生成分析报告，**不构成投资建议，也不是完整的安全审计**。

## 当前状态

🚧 **开发中**。M0～M4 已完成：脚手架、Ethereum JSON-RPC 取证与解析、7 条确定性风险规则、REST/SSE、中文降级报告、PostgreSQL 历史记录、OpenAPI，以及可复现的 Anvil + Docker Compose 本地演示。无 LLM Key 时返回 `llmUsed:false`、`degraded:true`，确定性链路仍可工作。下一阶段为 M5 Agent 编排。设计与里程碑见 [设计文档.md](设计文档.md)。

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

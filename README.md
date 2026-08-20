# ChainLens AI

基于证据的 Ethereum 链上交易分析 Agent：输入交易哈希，从 Ethereum JSON-RPC 取证，用确定性规则引擎判定风险，由 AI 生成带证据（evidence）、规则编号（ruleId）、置信度（confidence）的中文结构化分析报告。

> ⚠️ 本工具基于公开链上数据自动生成分析报告，**不构成投资建议，也不是完整的安全审计**。

## 当前状态

🚧 **开发中**。M0～M3 已完成：脚手架、Ethereum JSON-RPC 取证与解析、7 条确定性风险规则、REST/SSE、中文降级报告、PostgreSQL 历史记录与 OpenAPI。无 LLM Key 时返回 `llmUsed:false`、`degraded:true`，确定性链路仍可工作。下一阶段为 M4 Anvil 本地演示与 Docker Compose。设计与里程碑见 [设计文档.md](设计文档.md)。

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

## 快速开始（V1 完成后生效）

```bash
cp .env.example .env   # 填入 DEEPSEEK_API_KEY（可选：无 Key 走降级报告）
docker compose --profile demo-offline up
```

## 技术栈

Java 21 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL + pgvector · Anvil · Vite

## 许可证

Apache-2.0，见 [LICENSE](LICENSE)。

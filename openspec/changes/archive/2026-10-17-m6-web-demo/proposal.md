# Proposal

## Why

ChainLens 的后端能力已经可以通过 REST/SSE 演示，但目前主要依赖 curl 和 Swagger，面试者难以在一分钟内看清“输入交易 → 工具步骤 → 风险证据 → 中文报告”的完整价值。现有 `web/` 只有 Vite、类型、API 和 SSE 解析骨架，需要补成可测试、可打包、可截图的单页演示。

## What Changes

- 完成交易哈希或含哈希问句的输入、格式提示、提交锁定和重试状态。
- 使用 POST SSE 实时渲染阶段、规则与 Agent 工具调用时间线，并在完成事件中展示最终结果。
- 展示交易摘要、成功/失败、LLM/降级状态、风险 findings、evidence 原始定位、来源和局限。
- 使用浏览器 localStorage 保存最近分析 ID，并通过已有历史查询接口恢复结果；不新增后端列表 API。
- 增加响应式、键盘可用、减少动态效果兼容和安全文本渲染。
- 增加 Vitest 关键交互与 SSE 分块测试，以及 `-Pweb` Maven 静态资源打包和 Docker 构建集成。
- 产出演示截图并更新 README 的 M6 使用说明。
- 不修改现有 REST/SSE 数据契约，不削弱 evidence、ruleId、confidence 或确定性降级信息。

### In Scope

- 单页中文演示 UI、轻量本地历史、加载/成功/失败/降级状态。
- 现有 `/api/v1/analyses`、`/{id}` 和 POST SSE 的浏览器客户端。
- 前端单元测试、构建测试和本地浏览器验收。
- Maven 与 Docker 中的前端静态产物集成。

### Out of Scope

- 不增加登录、权限、钱包连接、交易签名或自动交易。
- 不实现钱包地址画像、图表大屏、移动原生应用或多页面路由。
- 不引入 React/Vue、组件库、SSR、WebSocket 或新的后端历史列表端点。
- 不在本变更中补做真实 DeepSeek smoke 验收，也不实现 M7 RAG。

## Capabilities

### New Capabilities

- `web-analysis-demo`: 用户在浏览器中提交 Ethereum 交易、查看 SSE 时间线、检查带证据的结构化报告、恢复最近分析，并从单一 Spring Boot 制品访问页面。

### Modified Capabilities

无。Web 页面消费现有 `analysis-api-history` 与 `agent-report-orchestration` 契约，不改变它们的服务端行为。

## Impact

- 主要影响 `web/`，补充入口逻辑、渲染、样式、测试和依赖锁文件。
- `chainlens-app/pom.xml` 增加显式 `web` profile；`chainlens-app/Dockerfile` 增加可复用的前端构建阶段或等价集成。
- 可能调整前端 TypeScript 类型，使其与 Java record 的真实 JSON 字段完全一致，但不改变后端契约。
- 更新 README 和 `docs/screenshots/`；默认 Java 构建仍不强制安装 Node，只有 `-Pweb` 或 Docker 完整制品构建包含前端。

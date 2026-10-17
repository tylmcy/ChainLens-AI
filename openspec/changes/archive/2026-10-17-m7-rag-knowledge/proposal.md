# Proposal

## Why

M5 的 `searchKnowledge` 目前仍返回空结果，报告只能给出交易事实和规则结论，无法从受控资料中补充“为什么危险、术语是什么意思、用户下一步该做什么”。M7 要补齐最小 RAG 闭环，同时保持链上事实只来自 Ethereum JSON-RPC，知识库失效时也不影响确定性分析主链路。

## What Changes

- 新增由 Git 管理的中文知识资料，覆盖 7 条风险规则、Ethereum/DeFi 基础术语和常见安全提示，目标形成 20～40 个高质量片段。
- 新增确定性 Markdown 切块、内容哈希去重、pgvector 幂等入库以及启动同步能力；提供仅本地管理配置启用的手动重建入口。
- 使用 Spring AI Transformers 在本地运行 BGE-small-zh-v1.5 ONNX 嵌入，默认 512 维；模型文件通过可配置缓存目录复用，不提交到 Git。
- 用 pgvector 实现 `KnowledgeSearch`，默认返回 top-3 片段及来源文件、标题、关联 ruleId 和相似度；无结果或基础设施不可用时返回明确的不可用状态。
- 将检索结果接入现有 `searchKnowledge` Agent 工具和报告来源，知识出处标为 `KNOWLEDGE`；知识片段只能用于解释与建议，不能产生或证明交易事实、finding、ruleId、confidence 或原始数据定位。
- 增加 10～15 条中文检索评测用例、Testcontainers 集成测试和 CI 缓存/评测步骤，输出 top-3 命中率。
- 不升级 Spring Boot/Spring AI 大版本；M7 继续使用项目锁定的 Java 21、Spring Boot 4.1 和 Spring AI 2.0.x 依赖基线。

### 范围边界

**范围内**：仓库内 Markdown 知识、标题级确定性切块、本地 ONNX 嵌入、pgvector 存储与检索、Agent 工具接入、知识来源标注、降级、检索评测和本地运维说明。

**范围外**：外部网页或白皮书抓取、用户上传文档、联网搜索、混合检索、重排序、知识图谱、多租户知识库、用 RAG 判断链上事实，以及 M8 的 MCP 暴露和 M9 的完整交易评测集。

## Capabilities

### New Capabilities

- `rag-knowledge-retrieval`: 定义受控知识内容、幂等入库、向量检索、来源追溯、不可用降级、手动同步和检索评测行为。

### Modified Capabilities

- `agent-report-orchestration`: 增加知识工具的事实隔离、知识来源标注及知识库不可用时的受控降级要求。

## Impact

- 主要影响 `chainlens-knowledge`、`chainlens-agent`、`chainlens-app`、`chainlens-api`、`docs/knowledge`、数据库初始化、Docker Compose 和 CI。
- `chainlens-knowledge` 增加 PgVector VectorStore 与 JDBC 依赖；运行 RAG 时会首次下载并缓存约百 MB 级 ONNX 模型，Apple Silicon 使用 CPU 推理，不引入 GPU 或常驻外部服务。
- PostgreSQL 新增独立知识向量表及必要扩展；分析历史表与既有 REST/SSE 合约保持兼容。
- 外部 RPC、LLM 或知识库任一不可用时，交易取证、确定性规则和模板报告仍可独立运行；重要结论继续保留 evidence、ruleId、confidence 和原始数据定位。

# Design

## Context

参见 [proposal.md](proposal.md) 的动机。当前 `chainlens-agent` 已暴露 `searchKnowledge` 工具和 `KnowledgeSearch` 接口，但应用只装配 `NoopKnowledgeSearch`；`chainlens-knowledge` 只有 Transformers 依赖和一个默认跳过的 ONNX smoke 测试。PostgreSQL 已使用 pgvector 镜像，分析历史通过 `schema.sql` 初始化。

项目固定 Java 21、Spring Boot 4.1.0 和 Spring AI 2.0.0。Spring AI 官方文档确认 2.0.x 的 Transformers starter 使用 `spring.ai.model.embedding=transformers` 与 `spring.ai.embedding.transformer.*` 配置；PgVector starter 默认不建表，必须显式启用 schema 初始化。M7 不顺带升级到 2.0.1，以免把依赖迁移风险混入 RAG 功能。

版本与配置依据：[Spring AI Transformers (ONNX) Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html)、[Spring AI PGvector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)。

## Goals / Non-Goals

**Goals:**

- 在单体应用内形成“Markdown → 确定性切块 → 本地嵌入 → pgvector → `searchKnowledge`”闭环。
- 每个命中可追溯到 Git 中的知识文件、标题、ruleId 和内容哈希。
- 让知识检索可独立失败；默认启动、M4 离线演示和确定性报告不下载模型也不依赖向量库。
- 用真实 BGE-small-zh-v1.5 给出可重复的 top-3 检索评测结果。

**Non-Goals:**

- 不让知识库参与交易事实、finding 或 evidence 的生成与校验。
- 不引入独立服务、Ollama 常驻进程、重排序器、BM25 或外部文档抓取。
- 不改变已有交易分析请求与历史查询接口；M7 的管理入口不属于面向公网的产品 API。

## Decisions

### 1. 继续使用本地 Transformers，先设置兼容性闸门

默认实现使用 `spring-ai-starter-model-transformers` 与 BGE-small-zh-v1.5 ONNX，512 维、CPU 推理。实施第一步在 Apple Silicon M5 上运行现有 smoke 测试，确认模型输出节点、tokenizer padding、512 维输出和原生库均正常；若失败，暂停 apply 并更新本设计后再切换 Ollama，不能静默改变方案。

模型与 tokenizer URL 可配置，缓存目录使用 `CHAINLENS_EMBEDDING_CACHE_DIR` 指定的持久路径；模型二进制和缓存不提交 Git。`demo-offline` 保持 `embedding:none`，因此 M4/M6 的十分钟路径不增加下载时间。

备选方案：Ollama + bge-m3 兼容性通常更直观，但要求额外安装和常驻进程，不适合作为默认个人项目启动路径；外部 embedding API 会引入 Key、网络和费用，也不采用。

### 2. 知识端口归属 `chainlens-knowledge`

把 `KnowledgeSearch`、命中对象和不可用状态从 `chainlens-agent` 移到 `chainlens-knowledge`。`chainlens-agent` 已依赖 `chainlens-knowledge`，因此不会形成循环依赖；Agent 只消费端口，不知道 pgvector 或 ONNX。知识模块提供：

- `NoopKnowledgeSearch`：RAG 未启用时返回 `available=false`；
- `PgVectorKnowledgeSearch`：嵌入查询并返回有来源的命中；
- `KnowledgeIngestionService`：解析、校验并同步受管知识集。

搜索返回结构区分 `available=false` 与 `available=true, hits=[]`，避免当前以“空列表”同时表示无匹配和故障的歧义。

### 3. 使用简单、可审查的 Markdown 契约

`docs/knowledge/*.md` 每个文件使用 YAML front matter：`id`、`title`、`category`、`ruleIds`、`version`，正文按二至四级标题组织。只允许从固定 classpath/仓库目录加载，不接受请求参数提供路径或 URL。

切块规则是确定性的：先按标题形成章节，短章节合并，长章节按段落切成约 300～500 个中文字符并保留不超过 80 字重叠。片段 ID 为 `SHA-256(sourcePath + headingPath + normalizedContent)`；元数据保存 source、title、heading、category、ruleIds、version、contentHash。这样同一 Git 版本在不同机器得到相同片段。

备选方案：Spring AI 通用 token splitter 更省代码，但中文字符与模型 token 的对应不直观，且标题来源容易丢失；V1 采用小型自定义切块器更容易教学、测试和复现。

### 4. PgVectorStore 负责向量检索，独立表避免污染分析历史

启用 `spring-ai-starter-vector-store-pgvector`，使用 `knowledge_chunks` 表、512 维、HNSW 与 cosine distance。配置显式开启 schema 初始化；`analyses` 表保持不变。表名固定，不从用户输入读取，并启用 schema/table 名校验。

同步流程先完成全部文档解析与元数据校验，再在事务内写入目标片段、删除受管目录中已过期的片段。20～40 个片段规模很小，不需要批处理队列或单独 ingestion 服务。重复同步通过稳定 ID 与内容哈希跳过未变化片段，并输出 added/updated/deleted/unchanged/failed 统计。

备选方案：自写 pgvector SQL 能完全控制 schema，但会重复 Spring AI 已提供的向量查询与元数据过滤能力；V1 只在幂等清单核对需要时使用小范围 JDBC，不重写相似度查询。

### 5. RAG 默认关闭，启动同步失败不阻止应用

只有同时配置 `chainlens.knowledge.enabled=true` 与 `spring.ai.model.embedding=transformers` 时才装配真实实现。默认及 `demo-offline` 继续使用 Noop。启用后，在应用 ready 之后执行一次同步；同步期间搜索返回“尚未就绪”，失败则记录诊断信息和指标，应用健康与确定性分析保持可用。

手动同步采用条件启用的 `POST /api/v1/knowledge/sync`：仅当 `chainlens.knowledge.admin-enabled=true` 且请求来源为 loopback 时注册/执行，只同步固定知识目录，不接受路径或 URL。返回同步统计；默认配置下端点不存在。这样满足本地教学和排错需求，又避免公开部署时留下无认证的重建入口。

### 6. 知识来源必须经过工具结果白名单

`searchKnowledge` 工具返回 `available`、`reason` 和结构化 hits。每个 hit 使用稳定 source reference，例如 `docs/knowledge/erc20-approval.md#无限额度授权`。Agent 在本次分析中记录实际返回过的知识来源；最终报告只允许引用该集合中的 `KNOWLEDGE` source。

事实一致性校验仍只用 TxFacts、原始 RPC 和确定性 findings。知识中的地址、数字和案例不进入事实白名单；知识不能新增 finding、修改 ruleId/confidence 或替代 evidence。检索不可用时，若 Agent 曾尝试检索，报告 limitations 明确说明知识增强未使用；模板降级报告不依赖 RAG。

### 7. 分层测试避免默认构建下载大模型

- 单元测试使用固定文本和 fake embedding，覆盖 front matter、切块、稳定 ID、无效文档和工具输出。
- `@Tag("db")` Testcontainers 测试使用与 Compose 相同的 `pgvector/pgvector:0.8.6-pg17-bookworm`，覆盖表初始化、幂等同步、过期清理、相似度阈值和数据库故障降级。
- 真实 ONNX smoke 与 12 条检索评测放入显式 `rag-eval` Maven profile/CI job；CI 缓存模型目录，输出 JSON 与 Markdown 摘要，top-3 命中率门槛 90%。默认 `mvn verify` 不联网下载模型。
- Agent 测试验证知识来源白名单和“知识样例数字不能变成当前交易事实”。

### 8. 模型批次按工作类型切换

遵循项目既定三段式：规划、公共契约、模块边界、ONNX/pgvector、事务与集成排错使用 `gpt-5.6-sol` 中等；连续编写知识 Markdown、固定评测问句、README 使用 `gpt-5.6-luna` 中等；里程碑结束再用 `gpt-5.6-sol` 中等集中审查。不会按单个文件频繁切换模型。

## Risks / Trade-offs

- [BGE ONNX 的输出节点或 arm64 原生库与当前 artifact 不兼容] → apply 首步运行真实 smoke；失败即停下并更新设计，不把 Ollama 回退混成隐式改动。
- [首次下载模型慢或 Hugging Face 不可达] → 缓存目录可配置、CI 缓存模型；默认模式不启用 RAG，失败只降级知识能力。
- [向量阈值过高漏召回、过低引入无关片段] → 阈值配置化，以 12 条固定评测校准并锁定 top-3 ≥90% 基线。
- [知识文档被误当作交易证据] → 工具、提示词、来源白名单和事实校验四层隔离；测试注入带地址和金额的恶意/误导片段。
- [启动同步与查询竞态] → 搜索暴露 NOT_READY 状态；只有完整同步提交后才标记 ready。
- [Spring AI 自动建表产生不可控变更] → 固定 schema/table/dimension/index 配置并用 Testcontainers 校验；禁止生产配置使用 remove-existing-table。
- [本地资源占用增加] → 仅启用 RAG 时加载模型；预期下载约百 MB、CPU 推理，文档规模限制在 20～40 个片段，不引入 GPU。

## Migration Plan

1. 先通过 Apple Silicon ONNX smoke 风险闸门。
2. 增加知识端口与文档解析，不接入应用，先完成纯单元测试。
3. 增加 PgVectorStore 配置和 Testcontainers 同步/检索测试。
4. 条件装配启动同步与本地管理端点；保持默认 Noop 配置。
5. 接入 Agent 工具、来源白名单、提示词与降级测试。
6. 增加真实模型检索评测、CI 缓存和 README 操作说明。

回滚时关闭 `chainlens.knowledge.enabled` 即恢复 Noop，不影响分析历史；新增 `knowledge_chunks` 表可保留，不参与主链路。若需要彻底移除，使用明确数据库迁移删除该表，禁止应用启动时自动删表。

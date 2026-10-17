# Tasks

## 模型批次说明

- 高能力实现批次：`gpt-5.6-sol / 中等`，连续处理 ONNX 风险闸门、公共契约、模块边界、PgVector、事务、Agent 护栏、Maven/Docker、集成排错和最终审查。
- 低能力连续批次：`gpt-5.6-luna / 中等`，一次性处理知识 Markdown、固定评测问句、README 和演示记录。
- 不按文件切换模型；低能力批次遇到公共 API、事实边界、构建、arm64 或连续两次修复失败时，升级到高能力批次。

## 1. ONNX 风险闸门（高能力实现批次）

- [x] 1.1 在 Apple Silicon M5 上使用锁定的 BGE-small-zh-v1.5 模型运行 `LocalEmbeddingSmokeTest`，验证 tokenizer、输出节点、512 维向量和原生库；记录下载大小、首次耗时与缓存位置，若失败则停止实施并先更新 design
- [x] 1.2 将模型 URI、tokenizer URI、输出节点、padding 和缓存目录整理为显式配置，验证默认 `mvn verify` 仍不下载模型且 `demo-offline` 继续使用 `embedding:none`

## 2. 知识契约与确定性切块（高能力实现批次）

- [x] 2.1 把 `KnowledgeSearch` 端口、可用状态和带 source/title/ruleIds/similarity 的命中类型迁入 `chainlens-knowledge`，更新 Agent 依赖并用编译与契约单测证明无循环依赖
- [x] 2.2 实现受限 Markdown front matter 解析和字段校验，为非直观边界添加中文注释，并用单元测试覆盖合法文档、缺字段、非法 ruleId、路径/URL 注入和空正文
- [x] 2.3 实现按标题、段落和 300～500 字目标长度切块，以及不超过 80 字重叠、规范化和 SHA-256 稳定 ID；用重复运行、长段落、中文标点和标题继承测试验证确定性
- [x] 2.4 实现 `NoopKnowledgeSearch` 的明确不可用结果，验证 RAG 关闭时 search 与现有分析链路均不访问模型或向量数据库

## 3. 受控知识内容（低能力连续批次）

- [x] 3.1 在 `docs/knowledge/` 编写覆盖 7 条风险规则的中文解释、危害、核对方式和应对建议，运行文档校验确认 ruleId 与规则引擎完全对应且不含真实私钥、助记词或投资承诺
- [x] 3.2 连续补齐 Ethereum/DeFi 术语和常见安全提示，使最终切块数落在 20～40，并输出 source/title/ruleId 清单供人工抽查
- [x] 3.3 编写 12 条版本化中文检索评测问句与预期 source/ruleId，覆盖同义表达、术语、规则解释和至少 2 条无关查询，并通过数据集 schema 校验

## 4. PgVector 入库与检索（高能力实现批次）

- [x] 4.1 增加 Spring AI PgVector 依赖和条件配置，固定 `knowledge_chunks`、512 维、HNSW、cosine distance 与 schema 校验；用上下文启动测试验证 RAG 开/关两种 Bean 图
- [x] 4.2 实现事务化幂等同步和 added/updated/deleted/unchanged/failed 统计，用 fake embedding + Testcontainers 验证首次同步、重复同步、内容修改、删除过期片段及失败回滚
- [x] 4.3 实现 top-3、阈值过滤和来源元数据映射，用 Testcontainers 验证命中、可用但空结果、数据库不可用与嵌入失败四种状态
- [x] 4.4 增加应用 ready 后的非阻塞启动同步与 NOT_READY 状态，验证同步中/同步失败不影响健康检查、REST 交易分析和确定性报告
- [x] 4.5 增加条件启用且仅允许 loopback 的 `POST /api/v1/knowledge/sync`，验证默认 404、远端拒绝、不接受路径/URL，以及本地调用返回同步统计

## 5. Agent 与事实隔离（高能力实现批次）

- [x] 5.1 将真实知识搜索接入现有 `searchKnowledge` 工具，返回 available/reason/hits 并保留工具步骤与成功率指标；用固定结果测试 JSON 契约
- [x] 5.2 更新系统提示词和本次分析的知识来源白名单，只允许报告引用工具实际返回的 `KNOWLEDGE` source；测试未知知识来源会触发重试或确定性降级
- [x] 5.3 扩展事实一致性测试，注入包含伪交易哈希、地址和金额的知识片段，验证它不能新增 finding、修改 ruleId/confidence 或替代 evidence/JSON Pointer
- [x] 5.4 覆盖知识库不可用、无命中、有命中三条 Agent 路径，验证前两者不伪造来源且主链路继续，有命中时同时保留 CHAIN 证据和 KNOWLEDGE 解释来源

## 6. 真实检索评测与文档（低能力连续批次）

- [x] 6.1 实现显式 `rag-eval` profile 的真实 ONNX 检索评测，输出逐例 JSON 与 Markdown 汇总，验证 12 条用例 top-3 命中率达到 90% 门槛
- [x] 6.2 更新 GitHub Actions 的独立 RAG 评测 job 和模型缓存，验证普通 `mvn verify` 无联网模型下载，RAG job 能复用缓存并保存评测 artifact
- [x] 6.3 更新 README，讲清 RAG 的大白话作用、事实边界、首次下载、缓存、启用/关闭、手动同步和常见错误，并按文档从关闭状态和启用状态各启动一次
- [x] 6.4 提供 M7 学习验收清单：用户需能解释向量检索、独立新增一篇知识文档、复现一次幂等同步与 top-3 评测，并排查一次模型/pgvector 故障；不得仅以测试通过宣称掌握

## 7. 里程碑集中审查（高能力审查批次）

- [x] 7.1 运行 `mvn verify`、DB 集成测试、真实 `rag-eval`、OpenSpec strict 和 Docker 默认演示回归，集中分析失败归因并确认 M0～M6 行为未回退
- [x] 7.2 人工演示一条无限授权交易：SSE 工具步骤出现 `searchKnowledge`、报告同时展示 CHAIN evidence 与可追溯 KNOWLEDGE source；关闭 RAG 后同一交易仍返回确定性报告
- [x] 7.3 自检无 TODO/TBD、无模型缓存或密钥入库、无用户可控路径/URL、无知识事实越权，并整理小粒度 Conventional Commits；学习验收单独保留为用户待完成项

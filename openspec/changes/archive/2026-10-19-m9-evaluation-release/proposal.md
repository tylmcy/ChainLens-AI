# Proposal

## Why

ChainLens AI 已具备交易取证、确定性规则、Agent 报告、RAG、Web 演示和 MCP 能力，但目前缺少一套能重复运行、能阻止质量回退的交易级评测证据。M9 需要优先补齐完全离线的评测和 CI 门槛，再完成可选的模型评测与发布材料，使 V1 的准确性、可复现性和简历展示质量都能被第三方验证。

## What Changes

- 建立版本化的 30 条交易评测集：至少 15 条来自 Anvil 可复现场景，至少 15 条来自公开 Ethereum 历史交易的冻结 JSON-RPC 数据；每条用例包含来源、预期解析结果、预期 `ruleId` 和可核验的原始数据定位。
- 优先实现不依赖网络、数据库和 LLM 的 offline 评测，直接复用生产交易解析器与确定性规则引擎，统计解析准确率和规则准确率，并生成 JSON、Markdown 两种报告。
- 在 GitHub Actions 增加 offline 评测门槛：解析准确率低于 98% 或规则准确率低于 100% 时构建失败；默认 CI 不读取模型密钥、不访问公网 RPC，也不产生模型费用。
- 提供手动触发的 full 评测，用于统计工具调用成功率、事实一致率、P50/P95 延迟和模型成本；模型或外部服务不可用时明确报告失败，不用伪造结果替代。
- 为评测产物记录 Git commit、数据集版本、规则版本、执行模式、模型和提供商等复现元数据；差异明细保留 `caseId`、期望值、实际值、`ruleId`、`evidence` 和原始字段/日志位置。
- 完成 V1 发布准备材料：README 快速启动、架构图、演示截图、示例报告、最新评测摘要，以及 Apple Silicon Mac 上约 10 分钟冷启动演练记录。
- **范围内**：Ethereum 单笔交易 V1、冻结数据回放、七条确定性规则、现有 Agent 的只读分析链路、CI 质量门槛和公开仓库展示材料。
- **范围外**：扩展到 100 条用例、实时全链监控、钱包历史、BTC、多 Agent、自动交易、压力/容量测试、主观文风评分、公开部署，以及在每次 PR 中运行付费 LLM 评测。
- 保持证据边界：链上事实仍只来自 JSON-RPC 冻结响应或受控 Anvil 响应；LLM 结果不能修改解析事实、规则结论或离线门槛。未配置 LLM 时，offline 评测与确定性报告仍必须完整工作。

## Capabilities

### New Capabilities

- `transaction-evaluation`: 定义版本化交易评测集、offline/full 双模式执行、六项指标、阈值、差异明细和可复现报告。
- `release-readiness`: 定义 CI 评测门槛、手动 full 工作流、公开仓库材料和约 10 分钟本地启动验收。

### Modified Capabilities

- `deterministic-risk-analysis`：M9 的严格证据校验发现风险地址命中时原始定位可能错误；修正为指向实际命中的交易参与方或日志 topic，不改变规则触发条件。同时以经核验的 `0x42966c68` 双签名碰撞覆盖 `AMBIGUOUS`，避免对 burn calldata 做无 ABI 的单候选断言。

## Impact

- 主要影响 `chainlens-evaluation`、`eval/`、`.github/workflows/`、README、架构图、截图和示例报告。
- offline 执行器将依赖现有 `chainlens-chain` 与 `chainlens-rules` 的公开接口；不得复制生产解析或规则逻辑。
- full 执行器将通过现有应用/API 或 Agent 边界采集指标，不新增微服务，不改变 REST/MCP 公开契约。
- CI 新增确定性质量门槛和评测报告产物；付费模型评测仅允许手动触发并受环境变量与预算上限约束。
- 不引入必须依赖本机原生二进制的新组件；Java 21、Docker 镜像及演练流程须兼容 Apple Silicon/arm64，同时保证 GitHub Actions Linux runner 可运行。

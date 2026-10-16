# Proposal

## Why

ChainLens 已完成 M0～M5 的大部分工程能力，但当前事实只分散在《设计文档》、README、代码和测试中，后续变更缺少可以机器校验的需求基线。现在建立 OpenSpec 基线，可以让 M6～M9 的每项新增行为都有明确的现状参照、验收场景和变更边界。

## What Changes

- 把已由代码、自动化测试或 M4 本地演示核实的行为整理为五组主能力规范。
- 固化“JSON-RPC 提供事实、确定性规则优先、LLM 可降级”的证据边界。
- 记录 REST/SSE、历史保存、离线 Anvil 演示以及可选 Agent 编排的当前外部行为。
- 明确本基线不证明真实 DeepSeek 调用已经验收；该项仍按《设计文档》的里程碑标准单独验证。
- 不修改业务代码、API、数据库结构或运行时依赖，不引入破坏性变更。

### In Scope

- M0～M5 中已经能够从仓库代码、测试和本地验收记录核实的行为。
- 重要结论的 evidence、ruleId、confidence 和原始数据定位约束。
- LLM 不可用时仍可完成确定性分析的降级行为。

### Out of Scope

- 不补做真实 DeepSeek smoke 测试、截图或录屏。
- 不实现 M6 Web 页面、M7 RAG、M8 MCP 或 M9 评测发布能力。
- 不回写或替代《设计文档》中的总体架构和长期路线图。

## Capabilities

### New Capabilities

- `ethereum-transaction-evidence`: 从 Ethereum JSON-RPC 获取交易、回执、区块和合约代码，并形成可追溯的交易事实。
- `deterministic-risk-analysis`: 解析 ETH/ERC-20 行为并执行七条确定性风险规则。
- `analysis-api-history`: 通过 REST/SSE 提交分析、查看步骤并保存和读取 PostgreSQL 历史。
- `local-anvil-demo`: 在 Apple Silicon 兼容的 Docker Compose 环境中生成并验证七类本地交易场景。
- `agent-report-orchestration`: 在 ChatModel 可用时执行只读工具循环和报告护栏，不可用时回退确定性中文报告。

### Modified Capabilities

无。当前 OpenSpec 主规范为空，本变更只建立现状基线。

## Impact

- 新增 OpenSpec 主能力规范的来源变更，不改变现有实现。
- 规范覆盖 `chainlens-chain`、`chainlens-rules`、`chainlens-agent`、`chainlens-api`、`chainlens-app`、PostgreSQL、Docker Compose 与 Anvil 演示脚本。
- 后续 M6～M9 必须以这些主规范为现状基线编写 delta spec，避免设计文档、实现和验收标准继续漂移。

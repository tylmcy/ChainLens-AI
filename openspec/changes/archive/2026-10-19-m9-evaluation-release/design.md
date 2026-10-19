# Design

## Context

动机见 [proposal.md](./proposal.md)。当前 `chainlens-evaluation` 只有 RAG 数据集与检索测试，交易级执行器尚未实现；`chainlens-chain` 已通过 `EthRpcClient` 和 `TxFactsAssembler` 提供可替换的取证入口，`chainlens-rules` 的 `RuleEngine` 为纯 Java 确定性组件，适合直接回放 fixture。现有 CI 运行 Maven 验证与 RAG 评测，但没有交易准确率门槛。

已有 `data/anvil-demo/transactions.json` 仅列出 7 个风险场景，M9 需要扩展为 15 个 Anvil 正例、反例与边界用例，并增加 15 个公开历史交易 fixture。M8 工作区当前仍有未提交改动；M9 实施前先确认并整理 M8 基线，避免把两个里程碑混入同一提交。

## Goals / Non-Goals

**Goals:**

- 让 offline 评测在无网络、无 Docker、无数据库、无 LLM Key 的 Java 21 环境稳定运行，并优先进入 CI。
- 用生产 `TxFactsAssembler`、parser 和 `RuleEngine` 计算结果，评测代码只负责回放、比较、计分与报告。
- 固定数据集、指标分母和失败语义，使不同开发者与 CI 得到同一结论。
- full 模式复用现有 `AnalysisService` 和 provider 抽象，采集真实工具、事实一致性、延迟与费用数据。
- 在 Apple Silicon MacBook Air M5 和 GitHub Actions Linux runner 上使用相同的 Java 数据集与执行器。

**Non-Goals:**

- 不为评测另建服务、数据库、消息队列或前端。
- 不把 WireMock、Anvil、PostgreSQL 或联网模型加入 offline 执行路径。
- 不用 LLM-as-judge 或人工文风评分代替机械指标。
- 不在 M9 扩展规则数量、钱包分析范围或改写现有产品 API。
- 不自动创建发布 tag、push 远程仓库或部署外部环境。

## Decisions

### 1. 使用单一评测内核，提供命令入口与测试门槛适配器

在 `chainlens-evaluation` 内建立无 Spring 容器要求的评测内核，职责分为：数据集加载与校验、fixture RPC 回放、单用例执行、指标聚合、差异生成、JSON/Markdown 输出。命令入口接受 `--mode=offline|full`、数据集路径、输出目录、预算等显式参数；CI 测试适配器调用同一内核，并根据门槛把结果转换成 Maven 成功或失败。

选择该方式，而不是“只写一个 JUnit 测试”，因为评测结果还需要被本地用户、README 和手动工作流复用；也不做独立 Spring Boot 应用，以免为了一个批处理任务新增服务和端口。

### 2. 数据集清单与 JSON-RPC fixture 分离

建议目录：

```text
eval/
├── cases/
│   └── transaction-eval-v1.json
├── fixtures/
│   └── <case-id>/
│       ├── chain-id.json
│       ├── transaction.json
│       ├── receipt.json
│       ├── block.json
│       └── code.json
└── results/
    ├── offline/latest.json
    ├── offline/latest.md
    └── full/<run-id>.{json,md}
```

清单保存 case 元数据与 ground truth，fixture 保存原始 JSON-RPC result。二者通过 `caseId` 和交易哈希交叉校验。原始响应不嵌入 ground truth，避免人工改预期时无意改写事实。`latest` 文件由最新受控执行生成，CI 另以上传 artifact 为准；带 run-id 的 full 结果不默认全部提交，防止仓库被重复产物污染。

30 条用例固定为 15 条 Anvil 与 15 条公开历史交易。Anvil 部分覆盖七条规则的正例，并补足不命中、边界值、多事件和 calldata 三态；公开历史部分覆盖成功/失败、legacy/EIP-1559、ETH 转账、Transfer、Approval、合约创建或 EOA 等真实组合。历史 fixture 只在采集阶段联网，合入后 offline 不再访问 RPC。每条公开用例记录 chainId、txHash、采集日期、数据来源说明和自撰标注许可；不保存 RPC Key。

为实际覆盖 `AMBIGUOUS`，在生产精选 selector 表中手工保留经签名哈希核验的 `0x42966c68` 两个候选：`burn(uint256)` 与 `collate_propagate_storage(bytes16)`；没有可信 ABI 时不消歧。原 Anvil burn 场景将额外命中 `UNKNOWN-SELECTOR`，并以一笔公开主网 burn 历史交易替换一条重复的普通 ETH 样本，使两类来源都覆盖三态。只改变对现有 calldata 的事实表达，不改变七规则定义、阈值或服务接口；更新 ground truth 后重新跑完整 offline 与回归测试。

备选是把全部响应和预期塞进单个大 JSON，文件更少，但 review 冲突大、单例失败难定位，故不采用。

### 3. 通过 `FixtureEthRpcClient` 复用完整生产取证链

实现只读 `FixtureEthRpcClient`，按当前 `caseId` 返回冻结的 chainId、transaction、receipt、block 与 code 响应，再交给生产 `TxFactsAssembler`。这样 transaction/receipt/block/event/calldata/code 的解析和 ETH 变化计算都来自真实代码路径。相同 fixture 可由集成测试适配为 WireMock stub，但 offline 执行本身不启动 HTTP 服务。

备选 WireMock 回放更接近 HTTP，但会增加端口、序列化和启动开销，而 RPC HTTP 层已有独立集成测试；M9 的核心目标是解析与规则准确性，因此以内存接口回放为最小可靠方案。

### 4. 明确 ground truth 比较粒度与集合语义

解析计分单位是清单中声明的原子字段：状态、ETH 数值、事件字段和 calldata 三态/已解析字段。事件按稳定身份键后进行无序比较，保留重复事件计数，避免仅因 RPC 日志顺序或集合去重造成误判。清单对不适用字段使用显式 `applicable:false`，不进入分母；缺失但适用的字段按错误计，不允许跳过失败项来提高准确率。

规则计分单位是“用例 × 七条规则”的 210 个二分类判断，分别记录 TP/TN/FP/FN，再按规范公式计算。除了命中集合，还校验实际 finding 的 `confidence`、`evidence` 和原始位置结构；结构错误列为该规则判断失败，并进入差异明细。

证据定位复核揭示现有 `RISK-ADDRESS-HIT` 始终指向 `tx.from` 的缺陷。M9 将修正其证据指针，使交易接收方命中时指向 `tx.to`，事件参与方命中时指向回执中相应日志的 indexed topic；规则的参与方范围与触发条件不变。找不到对应原始日志时不得猜测日志下标。

阈值使用整数计数比较，避免浮点舍入让 97.99% 显示为 98.00% 后误过门槛。

### 5. Offline 门槛作为独立 CI job，先于 Full 模式交付

新增 `transaction-offline-evaluation` job，使用 JDK 21，精确运行交易评测门槛测试并始终上传 `eval/results/offline/`。该 job 不传入任何 RPC、数据库或模型环境变量。建议 Maven 命令只选择评测类，并设置聚合构建中无匹配测试的模块不失败，避免重复运行完整 DB 测试；现有 `unit-and-package` 仍负责普通测试与 Testcontainers。

offline 代码、30 条 fixture 和 CI 门槛组成 M9 的首个可独立验收切片。只有它们全绿后，才开始 full 模式与发布材料。这样 CI 质量收益不会被付费模型或文档工作阻塞。

### 6. Full 模式由应用装配 `AnalysisService`，评测模块不绑定模型厂商

`chainlens-evaluation` 提供 full runner 及 Spring 可注入的执行组件；`chainlens-app` 在显式 `evaluation-full` profile 下为其提供现有 `AnalysisService`、RPC 与 `ChatModel` 装配。执行器调用 Java 边界而非 Web 页面或 MCP，因此与 HTTP/UI 解耦，同时仍走真实 Agent 工具循环和护栏。模型选择继续由 Spring AI 配置决定，评测业务代码不引用 DeepSeek、OpenAI 或 Ollama 厂商类型。

30 条清单同时包含 chainId 31337 的 Anvil 与 chainId 1 的主网历史交易。full 装配必须分别读取本地 Anvil RPC 和只读主网 RPC 配置，按用例 `chainId` 选择对应的 `AnalysisService`；启动前及每笔取证后均核对实际 `eth_chainId`，不匹配即记为失败且不调用模型。缺少任一网络的 RPC 配置时，完整 30 条 full 运行在预检阶段失败；显式小子集只要求其涉及的网络。不得将主网交易交给 Anvil 后把“交易不存在”解释为模型或解析失败。

full 模式默认关闭，并要求显式数据集、provider、模型、单价和总预算。GitHub Actions 仅通过 `workflow_dispatch` 启动，凭据来自 secret；本地也只从环境变量读取。执行器先检查预算配置，逐用例累计，触顶后不再发起调用。

2026-10-19 根据 DeepSeek 官方模型页，真实验收的默认模型改为仍受支持的 `deepseek-flash`，不再使用退役的 `deepseek-chat`。Spring AI 2.0.1 Ollama starter 仅用于显式本地 provider 替换验收；默认应用仍不装配 ChatModel。Ollama 的输出上界配置为 `spring.ai.ollama.chat.num-predict`，与通用 `ChatOptions.getMaxTokens()` 对应。模型具体上下文长度和单价不写死在运行时代码中，执行前从官方资料核对并输入。

预算采用用户确认的保守预留：每次 `ChatModel.call` 前，按明确配置的输入 token 上界、该请求的输出 token 上界及输入/输出单价预留最坏情况费用；剩余额度不足则不发起该次模型调用。成功响应后只用 provider 实际返回的输入/输出 token 结算并释放未使用的预留；usage 缺失或调用异常时不推测实际费用，保留预留并停止本次 full 执行，报告已完成与未执行用例。配置的输出上界必须覆盖实际请求 `maxTokens`，输入上界必须来自操作者确认的模型上下文限制；若任一上界不可核对则拒绝启动。预留是应用侧的调用闸门，不代表对 provider 最终账单的绝对担保，隐藏重试或计费口径差异必须在结果限制中说明。

备选通过 REST 调用已运行应用，部署更独立，但引入数据库、端口和 HTTP 层噪声，与已批准的模块依赖设计不符；直接 new 厂商 ChatModel 又会破坏 provider 可替换原则，均不采用。

### 7. 复用并增强事实校验，新增无厂商的用量观测接口

将现有 `FactConsistencyValidator` 的机械检查提取为可返回“被检查 token 总数、通过数、违规明细”的评分结果，原 `validate` 行为委托给评分逻辑，避免生产护栏与评测有两套事实口径。full 报告以所有被检查哈希/地址/长数字为分母计算事实一致率，零 token 报告视为数据异常而非自动 100%。

为 Agent 增加 provider-neutral 的分析遥测回调，记录每次 ChatResponse 的输入/输出/总 token 以及 RPC、解析、规则、LLM 分段耗时；默认实现为空，full runner 注入收集器。费用由评测配置的输入/输出单价计算，并在报告中同时保存单价来源日期。该选择避免把费用字段强行加入现有 REST 返回结构，也不依赖厂商专用 SDK。

工具成功率直接用 `AnalysisResult.toolSteps` 的 `success` 字段计算；没有任何工具步骤但声称使用 LLM 的用例记为异常，不作为 100%。延迟用单调时钟采样，并报告样本数、P50、P95；V1 不设置延迟硬门槛。

### 8. 报告采用稳定 Schema，文本摘要由同一结果渲染

JSON 是事实源，包含 suite、run metadata、thresholds、metric numerators/denominators、case results、diffs、timings、usage 和 cost。Markdown 只从内存中的同一结果对象渲染，不单独计算指标。报告写入采用临时文件后原子替换，失败时不留下看似完整的半份 `latest`。

时间戳、绝对路径和随机 ID 不参与 offline 结果一致性比较；排序固定为 `caseId`、字段路径、`ruleId`。Git commit 无法读取时写 `unavailable`，但 CI checkout 应保留可解析 commit。

### 9. 发布材料从可核验产物引用，不手填漂亮数字

README 的评测表从已提交的 `latest.json`/`latest.md` 引用同一数据集版本和门槛；架构图源文件与导出图同时保存，示例报告使用实际 Anvil 交易结果并保留证据字段。截图不得包含 API Key、RPC 凭据或本机隐私路径。

10 分钟演练从干净 checkout 计时，前提是 Docker Desktop 已运行且必要镜像/依赖可正常下载；记录机器架构、开始/结束时间、命令、成功判据和遇到的问题。M5 本地也应使用 arm64 兼容镜像；offline 评测只运行 Java，不新增 native 库，预计额外内存为 fixture 与结果对象的几十 MB 级，不需要容器。

### 10. 模型使用按三个连续批次分配

不按文件频繁换模型，按连续任务批次执行：

1. **批次 A — `gpt-5.6-sol` 中等强度**：数据集公共 Schema、fixture 回放边界、计分口径、offline 执行器、Maven/CI 门槛及集成排错。该批次优先完成并单独验收。
2. **批次 B — `gpt-5.6-luna` 中等强度**：在既定 Schema 下连续补齐固定用例与来源说明，整理 README、架构图、示例报告、固定截图和启动演练记录；不得在此批次自行改变公共契约或阈值。
3. **批次 C — `gpt-5.6-sol` 中等强度**：full 模式、异步/预算中止、模型用量观测、手动工作流和里程碑集中审查；统一检查数据泄漏、事实口径、Maven/Docker、Apple Silicon 与 CI 行为。

若批次 B 发现需要改变 Schema、公共 API 或 CI 行为，暂停该批次并回到 `gpt-5.6-sol` 处理，而不是由低能力模型临时改设计。

## Risks / Trade-offs

- [30 条 ground truth 人工标注可能有误] → 每条用例保留原始定位与来源；正反例成对 review；首次 CI 门槛启用前由集中审查核对全部差异。
- [公开 RPC 对历史 `eth_getCode` 的支持不一致] → fixture 固化采集时的结果或明确错误语义；offline 不重新请求；用例注明 provider 与采集日期。
- [98% 字段级准确率可能被大量简单字段稀释] → 报告同时输出按类别准确率和逐例失败，不允许不适用字段进入分母；规则仍要求 100%。
- [Full 指标受模型随机性和外部服务波动影响] → full 不进入默认 PR 硬门槛，保存模型/provider/参数/时间与样本明细；工具和事实阈值用于验收报告，不阻塞 offline CI。
- [token usage 在不同 provider 元数据中缺失] → 明确记为 unavailable 并使成本指标不完整，不猜测 token；对应 full 执行不能宣称六指标完整通过。
- [RPC/模型异常可能回显 URL 中的凭据] → Full 差异、REST/SSE 与报告只输出异常类别或稳定错误码；生产 RPC 客户端不得把配置 URL 或节点原始错误文本放入公开异常。
- [运行 30 条 full 评测超预算] → 启动前要求预算和单价，逐用例检查硬上限，支持先运行显式子集做冒烟。
- [M8 未整理改动污染 M9 提交] → 实施前完成 M8 状态核对；M9 只暂存本 change 对应文件，保持小型 Conventional Commits。
- [提交 `latest` 报告产生时间戳噪声] → JSON 结构与排序稳定，更新最新结果只在受控里程碑验收时进行，普通 CI 仅上传 artifact。

## Migration Plan

1. 先确认 M8 归档与工作区基线，保留现有 RAG 评测行为。
2. 加入数据集 Schema、加载校验、fixture 回放和最小代表用例，以单元测试锁定计分口径。
3. 补齐 30 条数据、offline 执行器和双格式报告；在本地断网语义下运行并核对阈值。
4. 添加独立 CI job，先以分支运行验证 artifact 与失败语义，再把它设为正式门槛。
5. 完成 full runner、遥测、预算控制和手动工作流，不改变默认 CI。
6. 更新 README 与展示材料，执行 Apple Silicon 冷启动演练，最后集中审查 M9。

回滚时可先移除/禁用新增 CI job，恢复原 CI；评测模块和数据文件不改变生产数据库或 API，可独立回退。不得通过放宽阈值或删除失败用例作为回滚手段。

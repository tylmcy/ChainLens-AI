# M9 验证记录

## 开始前的工作区边界（2026-10-18）

- `HEAD` 为 `0130953`（M7 归档提交）；M8 的实现、主 spec 同步和归档文件仍在未提交工作区。
- 已核对 M8 归档目录 `openspec/changes/archive/2026-10-18-m8-mcp-server/` 和主 spec `openspec/specs/mcp-analysis-tools/spec.md` 均存在。
- 当前未提交的 M8 文件涉及 `.env.example`、`README.md`、`pom.xml`、`docker-compose.yml`、`设计文档.md`、`chainlens-app/`、`chainlens-mcp/`、`docs/mcp/`、`docs/screenshots/m8-*`、`scripts/start-mcp-stdio.sh` 以及上述 OpenSpec 目录。
- M9 新增文件位于 `openspec/changes/m9-evaluation-release/`；后续实现需按文件核对暂存范围，不覆盖或重置 M8 文件。
- `openspec list --json` 显示唯一活动变更为 `m9-evaluation-release`，开始时为 0/45。

## Maven 基线

- 直接在当前沙箱运行 `mvn -B -ntp verify` 时，WireMock 因本地端口绑定被拒绝而在 `chainlens-chain` 停止；错误为 `java.net.SocketException: Operation not permitted`，属于沙箱权限限制。
- 在允许本地端口与 Docker 的环境重跑同一命令：退出码 0、10 个 Reactor 项目全部 `SUCCESS`、耗时 26.330 秒；当前 Surefire XML 汇总为 161 个测试用例、44 份报告，未见失败。运行使用宿主机 Java 26.0.1，POM 编译目标为 Java 21。
- 后续 M9 回归需沿用可绑定本地端口的测试环境；单独的 offline 评测门槛应能在普通沙箱内运行。

## 批次 A：数据契约与回放

- 新增版本化数据集与结果 Java 类型、两份 JSON Schema；`jq empty` 对两份 Schema 均通过。
- `EvaluationDatasetLoaderTest` 5 个测试通过：30 条/15+15 合法集、序列化往返、必填 ground truth、缺失/冲突 fixture、重复 ID、来源比例、五个 RPC 方法回放及历史 code 不可查语义。
- 验证命令：`mvn -B -ntp -pl chainlens-evaluation -am test -Dtest=EvaluationDatasetLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`；无需 Docker、端口、RPC 或模型 Key。
- 生产链路与解析比较验证命令：`mvn -B -ntp -pl chainlens-evaluation -am test -Dtest=EvaluationDatasetLoaderTest,EvaluationRuleSetTest,ParsingComparatorTest -Dsurefire.failIfNoSpecifiedTests=false`；8 个测试通过。
- 规则四分类和整数门槛验证命令增加 `RuleComparatorTest`；合计 12 个测试通过。
- 30 条本地合成用例执行测试：`OfflineEvaluationRunnerTest` 3 个测试通过，验证 210 个规则判断、重复运行一致性、执行异常失败及双格式报告；这些合成用例仅是执行器测试数据，不计作 M9 正式 30 条评测集。
- 报告快照、顺序与无 Git 信息测试：`EvaluationReportWriterTest` 和更新后的 `OfflineEvaluationRunnerTest` 共 7 个测试通过；JSON 与 Markdown 均由同一结果对象写出，目录中无残留临时文件。

## Offline 断点续作（2026-10-19）

- 冻结数据集已包含 15 条 Anvil + 15 条公开主网历史交易，共 30 条、149 份 JSON-RPC 响应文件（合约创建无需 code 响应）。公开主网数据来自只读 RPC，采集过程遇到限流/空回执时更换公开节点并重试；历史 `eth_getCode` 不可查的响应保留为明确错误。
- 独立 `FrozenDatasetIntegrityTest` 对 30 条用例逐一核对哈希、区块、状态、wei 算式、selector、Transfer/Approval 原始 topics/data/logIndex 和每项预期 finding 的 JSON Pointer；测试通过。2026-10-19 另逐条目视核对了 30 份原始回执的状态、gas、selector、日志数量与全局 logIndex，并对照用例矩阵复核；此为工程标注审查，不代表维护者已通过 8.6 学习验收。
- `RiskAddressHitRuleTest`、`RuleEngineTest`、`RuleEngineDemoTest` 共 13 个测试通过，证明风险地址命中指向实际 tx.from/tx.to 或对应事件 topic；缺少原始日志会失败而不伪造证据。
- 正式 30 条 offline 结果：解析 585/585（100.00%），规则 210/210（TP=16、TN=194、FP=0、FN=0）。报告在 `eval/results/offline/latest.json` 和 `latest.md`；该数字仅代表当前固定样本，不代表所有 Ethereum 交易的真实世界准确率。
- `TransactionOfflineEvaluationGateTest` 3 个测试通过：正式数据过门槛；临时副本中改坏解析标注或规则预期均非零退出并保留差异报告，正式 manifest 未被修改。
- `.github/workflows/ci.yml` 新增独立 `transaction-offline-evaluation` job，使用 Java 21 和 `transaction-eval` Maven profile，`always()` 上传报告与 gate.log；Ruby YAML 解析成功，本地等价 Maven 命令通过。普通 `verify` 排除该标签，避免重复执行。
- 清空 `ETH_RPC_URL`、`DATABASE_URL`、`DEEPSEEK_API_KEY`、`OPENAI_API_KEY` 和 `OLLAMA_BASE_URL` 后，本地等价命令通过；该测试没有启动 Docker、数据库或模型，也不调用公网 RPC。
- 已按设计先后同步设计文档、OpenSpec 和实现：`0x42966c68` 的两个候选经 4byte.directory 交叉核验，生产精选表改为 `AMBIGUOUS`；Anvil burn 及新增公开主网 burn 样本都覆盖此状态。替换的是重复的 `historical-2024-eth` 普通转账，旧 fixture 移至可恢复的 `/private/tmp/chainlens-m9-replaced-historical-2024-eth-20261019`，未删除其他数据。当前正式数据集两类来源均覆盖 `RESOLVED/AMBIGUOUS/UNRESOLVED`。
- 重新运行 targeted gate 与原始数据审查测试均通过；最新结果仍为解析 585/585、规则 210/210，混淆矩阵更新为 TP=18、TN=192、FP=0、FN=0。`openspec validate m9-evaluation-release --strict` 通过；fixture/cases 秘密扫描无匹配。

## 发布材料与回归续验（2026-10-19）

- `docs/architecture.mmd` 和 `docs/architecture.svg` 已加入 README；SVG 通过 XML 语法检查，并在本地浏览器目视核对。离线虚线从冻结 fixture 进入生产解析与规则，不进入实时 RPC；浏览器检查期间临时静态服务器已停止。
- 用已运行的本地 ChainLens API 查询固定 Anvil 无限授权交易，生成 `docs/M9-Anvil-示例报告.md`；浏览器展开证据后截取 `docs/screenshots/m9-anvil-evidence.png`，人工检查画面展示 `ruleId`、置信度、`eth_getTransactionReceipt`、`/result/logs/0`，没有密钥或本机文件路径。这是 `llmUsed=false` 的降级演示，不能用于证明 full 模型评测。
- 直接沙箱中的 `mvn -B -ntp verify` 再次因 WireMock 无法绑定端口失败；在允许本机端口与 Docker 的环境重跑同一命令通过，10 个 Reactor 项目均成功，耗时 27.991 秒。该差异是沙箱权限，不是代码回归。
- 在清空 RPC/数据库/模型变量后重跑 offline gate + fixture 完整性测试，4 个测试通过，7 个 Reactor 项目均成功，耗时 1.530 秒；报告仍为解析 585/585、规则 210/210（TP=18、TN=192）。
- 真实 RAG 评测 `RagRetrievalEvaluationTest` 通过，耗时 02:16；本次首次补全 Hugging Face 与 DJL 的 Apple Silicon 模型/原生库缓存，测试使用 Testcontainers pgvector，不使用 LLM Key。Web 的 5 份 Vitest 文件共 13 个测试通过，`tsc -b` 与 Vite 构建通过。直接执行 `pnpm test` 触发 pnpm 在线元数据检查并因当前环境无法连接 npm、非 TTY 不允许清理 node_modules 而停止；改用已安装的本地 `node_modules/.bin` 运行同一测试与构建成功，没有改动依赖锁文件。
- 5.3 的独立照做演练、5.6 的干净 checkout 十分钟启动、6.5 的远端 GitHub Actions artifact 仍未验收；本地工作流验证不能替代远端 artifact。Full 模式按设计暂不启动。
- 用 `jq` 对照 `latest.json`：`parsingAccuracy=585/585`、规则 TP=18、TN=192、FP=0、FN=0，与 README 和 `latest.md` 一致；README 新增的架构图、截图、示例报告及评测链接均存在。M9 发布材料扫描未发现 TODO/TBD 或明显范围冲突；`xmllint` 验证 SVG 格式正确，`git diff --check` 通过。
- 离线报告已在 M9 评测代码提交 `ab34f84` 之后重新生成，`latest.json.gitCommit` 为 `ab34f840ea9964a168aca68d47f8adb557139dd6`，指向实际产生报告的代码版本。报告 JSON/Markdown 已通过单独文档提交公开到仓库；`.gitignore` 现在仅忽略临时评测产物，保留 README 链接所指的两份离线基准报告。
- 在 Apple Silicon MacBook Air M5 上，将当前实现复制为临时干净源码快照，排除 `.git`、`.env`、构建产物、Node 模块、数据卷和模型缓存；通过 Compose 独立项目名与 `18080/18545/25432` 备用宿主端口运行 `./scripts/demo-up.sh`。Docker 镜像层已缓存，实测完整启动与演示验收为 6.676 秒；首次干净快照成功构建、启动，并通过 8 个正向风险场景。随后查询无限授权交易，确认 `ANVIL`、`ERC20-UNLIMITED-APPROVAL`、`confidence=1.0` 与 `/result/logs/0`。演示不需要宿主机 Java、Node、Foundry 或 PostgreSQL，也未提供任何 API Key。测试后只删除项目名 `chainlens-m9-clean` 新建的容器、网络和数据库卷；原来 8080/8545/5432 的三项容器仍健康。由于镜像层来自现有 Docker 缓存，本记录的 6.676 秒不是首次联网下载镜像时长。
- README 首次入口、证据边界、M5 Compose 演示、环境变量、offline 门槛、full 当前状态、测试命令和免责声明均已核对；Maven、offline、RAG、Web 测试/构建与干净快照演练按文档命令验证。Full 尚未实现，README 明确标为未完成且没有给出不可运行命令；这不构成 full 功能验收。

## 集中审查清单（8.2，已完成静态与本地审查；真实 Full 运行另见 7.7）

| 检查项 | 已核对的证据 | 状态 |
|---|---|---|
| 30 条 ground truth 与原始 RPC 定位 | `FrozenDatasetIntegrityTest` 逐项核对哈希、回执、事件字段和 JSON Pointer；另有人工逐条回执复核记录 | 通过 |
| 指标分母和阈值 | `latest.json` 为 585 个解析字段、210 个规则判断；`RuleComparatorTest` 覆盖整数阈值边界，不使用显示舍入放宽门槛 | 通过 |
| 规则证据位置 | `RiskAddressHitRuleTest` 等测试覆盖 tx 地址和事件 topic，缺失日志时不得编造证据 | 通过 |
| 秘密处理 | fixture、案例清单、示例报告与截图无密钥扫描命中；`FullEvaluationRunnerTest` 的异常消息哨兵、`HttpJsonRpcClientIntegrationTest` 的 URL/节点消息哨兵、`ErrorMapperSecretTest` 均验证公开结果不回显凭据；clean snapshot 未带入 `.env` | 通过 |
| 报告稳定性 | `EvaluationReportWriterTest` 覆盖排序、双格式同源和原子写入；离线 JSON/Markdown 数字一致 | 通过 |
| 预算硬停止 | `ModelBudgetTest` 覆盖预留、结算、未知 usage；独立 18545 Anvil 的无效 key 预检在模型调用前停止，费用仍为零 | 本地审查通过；真实模型运行待 7.7 |
| M5 / CI 兼容性 | 本地 M5 快照演练、Java 21 Full jar 零付费预检、`mvn verify`、远端 offline 三 job 与 artifact 已验收；`full-evaluation.yml` 只可手动触发，且 `always()` 上传 | 设计/本地审查通过；Full 远端执行属于 7.7，未冒称完成 |

## 远端 CI 前置条件

- 已将用户提供的 `https://github.com/tylmcy/ChainLens-AI.git` 配置为 `origin`，只读查询确认远端当前没有分支。`gh auth status` 显示 GitHub CLI token 无效，因此还无法读取或触发 Actions run，也无法取回 artifact。CI 仅响应推送到 `main` 或面向 `main` 的 PR。
- 用户的项目约束禁止代理向远程仓库 push。该约束仍生效；在真实 Actions artifact 可供检查之前，6.5 保持未完成，Full 阶段 7.x 按设计不启动。

## 远端 CI 第一阶段验收（2026-10-19，覆盖上节历史状态）

- 用户已推送 `8d59a37` 到 `origin/main`。GitHub Actions [CI 运行 #1](https://github.com/tylmcy/ChainLens-AI/actions/runs/39192303469) 显示 `unit-and-package`、`rag-evaluation` 和 `transaction-offline-evaluation` 三个 job 均成功；离线 job 的 “Evaluate 30 frozen transactions” 步骤成功，耗时 23 秒，整个 job 耗时 31 秒。
- 运行页面列出 `transaction-offline-evaluation` artifact，大小 4.16 KB，SHA-256 digest 为 `baefa5790eb58becd51836f90d1cb1ee26cfa237cd1ad8e7b9cea1b01fa78537`；该证据确认远端实际上传了产物。本地已保存的完整 30 条报告为解析 585/585、规则 210/210（TP=18、TN=192、FP=0、FN=0），见 `eval/results/offline/latest.json` 和 `latest.md`。
- 5.2 与 6.3 的本地故障注入已分别验证错误标注导致非零退出、仍保留差异报告和 `always()` 上传配置；远端此次运行验证正式配置全绿。6.5 的三类证据已齐，允许开始 7.x Full 模式实现。此处未把本地报告误称为下载并核对过的远端 artifact 内容。

## Full 模式已完成的基础工作（2026-10-19）

- 7.1 将 `FactConsistencyValidator.validate` 委托给 `score`，返回被检查 token 数、通过数和原有完整违规列表；零 token 的分母保持为零。`FactConsistencyValidatorTest` 与 `AgenticAnalysisServiceTest` 合计 19 个测试通过，覆盖合法、编造和零 token，既有 Agent 护栏无回归。
- 7.2 在 `AnalysisProgressListener` 增加每次模型响应的通用 usage 与失败、分段耗时回调；`TxFactsAssembler` 用单调时钟区分 JSON-RPC 和解析装配时间，Agent 记录规则与每次 LLM 调用。Spring AI 的 `EmptyUsage` 被识别为未知，不把占位 0/0 当作真实零费用。相关 Agent 测试 21/21 通过；`TxFactsAssemblerIntegrationTest` 在允许 WireMock 绑定端口的环境 4/4 通过。普通沙箱中的同一集成测试因端口绑定被拒绝，这是权限差异。
- 7.3–7.4 实施前发现两处设计细节需明确：30 条集同时包含 Anvil 与主网历史交易，单一 `ETH_RPC_URL` 无法完整执行；远端模型的实际输入 token/隐式重试在调用前不可精确获知，单靠事后累计费用无法保证单次运行不越过硬预算。这两处不能通过伪造网络结果或估算费用掩盖。
- 用户确认双 RPC 路由后，已在根设计文档和 M9 design 中补充按 `chainId` 路由及双重网络核对。7.3 新增 `FullNetworkRouter` 和 `FullEvaluationRunner`：预检全部所选网络后逐例调用 `AnalysisService`，复用生产解析/规则比较器，统计工具、事实及最近秩 P50/P95。`FullEvaluationRunnerTest` 4/4 通过，覆盖正确路由、缺失或错误工具步骤、零事实 token、错误网络和小样本分位数。费用与模型配置仍属 7.4–7.6，尚未宣称 Full 可端到端运行。
- 用户确认预算保守预留后，已在两份设计中写明“调用前预留、usage 返回后结算、未知费用停止”的语义。7.4 新增 `ModelBudget`，按输入/输出 token 上界与 USD/百万 token 单价进行每次模型调用准入；缺 usage 或异常时保留不可核实预留，不能把它当作实际零费用。`FullEvaluationResult` 与同源 JSON/Markdown 保存逐笔费用、总额、单价日期、停止原因和未执行 caseId。`FullEvaluationRunnerTest` 7/7 与 `ModelBudgetTest` 4/4 通过，覆盖恰好触顶、超限前拒绝、未知 usage、错误网络及报告一致性。应用 profile、工作流与真实模型仍属 7.5–7.8，未宣称可端到端运行。
- 7.5 `FullEvaluationConfiguration` 只在显式 `evaluation-full` profile 存在，按 Anvil/mainnet 分别装配 `NetworkGuardedFactsProvider` 与同一 provider-neutral `ChatModel`；DeepSeek 选项 mock 的应用上下文装配测试 2/2 通过，普通 profile 无付费评测 runner。Full runner 使用已确认的 `maxTokens` 上界；缺失时在模型调用前拒绝。预检失败会生成不含 URL/密钥的 `full-error` 产物。
- 7.6 `.github/workflows/full-evaluation.yml` 仅声明 `workflow_dispatch`，预算、单价、日期和 token 上界均为必填输入；DeepSeek Key 与主网 RPC URL 仅从 GitHub secrets 注入环境。Ruby YAML/事件契约检查通过，`always()` 上传 `eval/results/full/latest/`，工作流先建立状态文件确保构建阶段失败仍有 artifact。真实付费运行尚未触发，当前不把本地配置验证当作 7.7 验收。

## Full 真实运行前的兼容性与安全复核（2026-10-19）

- 核对[DeepSeek 官方模型与价格页](https://api-docs.deepseek.com/quick_start/pricing/)：`deepseek-flash` 为现行模型名，1M 上下文、最大输出 384K；保守峰值 cache-miss 输入价为 $0.3/百万 token、峰值输出价为 $1.2/百万 token。旧 `deepseek-chat` 已退役，因此更新 `.env.example`、应用默认配置、手动 workflow 及本机被忽略的 `.env` 模型名；用户真实 Key 未被打印或提交。项目的单价仍要求每次执行显式输入，不依赖这组文档值自动计费。
- 增加 Spring AI 2.0.1 Ollama starter；官方属性 `spring.ai.ollama.chat.num-predict` 经 Spring AI 文档和本地 jar 的 `getMaxTokens()` 字节码核对。Ollama 允许 API 单价为零，付费 provider 不允许两项单价同时为零。应用在启动 Full runner 前核对声明的 provider/model 与实际 ChatModel 选项一致，防止模型名错配使费用报告失真。
- Docker 中原 8545 演示链保留不动，另以独立 Compose project 在 18545 启动 Anvil 并完成 demo-seed；固定 `anvil-finite-approval` 交易在新链上可查。用无效测试 key、$0.01 预算及官方 1M/1024 token 上界运行 `evaluation-full` jar；结果按设计非零退出，`eval/results/full/preflight/full.json` 显示 `Halt`、停止原因是下次最坏情况预留超额、`verifiedCostUsd=0`、LLM 耗时样本为零，证明未发出模型请求。该结果不能充当 7.7 真实模型验收。
- Full runner 不再复制原始异常消息到公开差异产物，避免 RPC URL 查询参数或模型错误信息泄漏凭据；新增 `SECRET_SENTINEL` 测试验证 JSON/Markdown 均无泄漏。修复原 M8 stdio 进程测试中对异步 `Started` 日志的竞态依赖：initialize、列工具、取证调用成功已是协议级验收，单独重跑通过。
- 集中审查继续追踪到生产 RPC/REST/SSE：网络异常不再拼接节点 URL 或保留可能带 URL 的底层 cause，JSON-RPC 节点原文不再进入公开异常，历史 `eth_getCode` 失败只留下“不可查”及异常类别；未知服务异常不再把原文返回给客户端。`HttpJsonRpcClientIntegrationTest`、`TxFactsAssemblerIntegrationTest`、`ErrorMapperSecretTest` 与 Full 报告哨兵测试均通过。具体能力边界已同步根设计文档 9.4。
- 对公开主网 RPC 做只读探测：PublicNode 公布的 `https://ethereum-rpc.publicnode.com` 能返回 chainId=1 与选定的 2020 历史交易，但对该交易区块的 `eth_getCode` 返回 HTTP 403；其页面另标有 Archive Access 入口。该节点目前不能被当作完整 30 条 Full 评测的可靠归档节点，不能把历史代码缺失误记为解析器正确。Full 30 条仍需可查历史状态的只读主网 RPC。
- 7.8 的本地前提检查：Ollama 已安装且服务可达，但 `ollama list` 只有 `qwen3-embedding:0.6b`，不能用于聊天/工具调用。尝试下载官方标为支持 tools 的 `qwen3:1.7b`（约 1.4 GB），网络吞吐长期偏低且下载进度从约 78% 回退到约 61%，本轮主动中断；再次 `ollama list` 仍无聊天模型，因此 7.8 保持未完成，不把已装依赖当成 provider 替换验收。独立 18545 评测 Anvil 已停止并移除，原 8080/8545/5432 演示容器仍健康。
- 新增 Ollama 依赖后的定向 Maven 测试通过；完整 `mvn -B -ntp verify` 首次因上述 stdio 日志竞态失败，修正后重跑 10 个 Reactor 项目全部成功，耗时 28.070 秒。随后增加的模型元数据核对、零费用和秘密测试的定向测试也通过。
- RPC/REST/SSE 去敏修正后的第二次完整 `mvn -B -ntp verify` 再次 10 个 Reactor 项目全部成功，耗时 32.688 秒；独立 offline gate 与数据完整性测试也通过，固定报告仍为解析 585/585、规则 210/210。

## 本地提交记录

## 单笔真实 DeepSeek Full 冒烟（2026-10-19）

- 用户仅批准以 2 USD 为上限运行 1 条 Anvil 交易；本次只选择 `anvil-finite-approval`，没有启动 30 条完整 Full 或远端工作流。执行前重新核对 [DeepSeek 官方价格页](https://api-docs.deepseek.com/quick_start/pricing/)：`deepseek-flash` 上下文 1M token；保守使用峰值 cache-miss 输入 0.3 USD/百万 token、峰值输出 1.2 USD/百万 token，并将每次请求输出上限配置为 1024 token。
- 独立 Compose project `chainlens-m9-full` 在宿主 18545 运行 Anvil，`demo-seed` 退出 0；RPC 查到固定交易哈希 `0x91c608e694505a132fc47f44da755abfe478724527fab69bc1ff023b3a13aae8`，chainId 为 31337。原 8080/8545/5432 容器未重置。最新 Full jar 构建成功，实际运行使用 `evaluation-full` profile、DeepSeek ChatModel 和被忽略的本机 `.env` 密钥，未在命令输出中打印密钥。
- 本机忽略提交的 `eval/results/full/deepseek-smoke/full.json` 与 `full.md` 记录：1/1 case 通过，解析 15/15，规则 7/7，工具 6/6，事实 9/9，无差异；端到端延迟 16.669 秒（单样本 P50=P95），其中 LLM 16.613 秒。模型 usage 已返回并由预算器结算，按本次配置单价计算的已核实费用为 0.009483 USD，不可核实预留为 0，预算未停止。此数字是应用根据 provider usage 计算的估值，未核对 DeepSeek 最终账单。
- 此次首份 Full 报告保存了模型/provider、单价、日期、成本、预算和计分分母，**但当时未逐次或逐笔输出原始输入/输出 token 数**。该缺口随后按设计第 7、8 节补齐，见下节重验；不得从首份费用倒推出看似真实的 token 数。此单笔结果不能代替 30 条 Full，也不能证明 Ollama provider 替换；7.7、7.8、8.6 保持未勾选。
- 冒烟后已仅停止并移除 `chainlens-m9-full` 专用 Anvil、seed 容器及网络；原项目 8080/8545/5432 三个容器仍健康。`git diff --check` 通过；未推送、部署或创建远端资源。

## token 用量补齐及单笔重验（2026-10-19）

- `FullEvaluationResult` 现在保存每次模型响应的 provider 原始输入、输出和 total token；每笔 case 有有序调用清单，顶层保存完整/缺失调用数及可核实合计。provider 不返回的字段保留 `null`，Markdown 标为“不可用”，不根据输入+输出或费用倒算。`FullEvaluationReportWriter` 的 JSON 与 Markdown 均投影同一结果对象，不改变既有解析、规则、工具或事实计分分母。
- `FullEvaluationRunnerTest` 与 `ModelBudgetTest` 定向运行 15/15 通过，覆盖逐次 usage、多次调用合计、缺失 usage 保留未知以及双格式输出；完整 `mvn -B -ntp verify` 10 个 Reactor 模块全部通过，耗时 29.010 秒，含 Docker/Testcontainers 集成测试。运行 jar 使用 Java 21.0.11；Maven 宿主为 Java 26.0.1、编译目标 Java 21。
- 重新核对 [DeepSeek 官方模型与价格页](https://api-docs.deepseek.com/quick_start/pricing/)后，对同一 `anvil-finite-approval` 交易再次执行真实 `deepseek-flash` Full：独立 Anvil chainId 31337、seed 退出 0、固定 txHash 与上节相同。本次应用预算为 1.90 USD；本地忽略提交的 `eval/results/full/deepseek-usage-acceptance/full.json` 与 `full.md` 记录 1/1 case 通过、解析 15/15、规则 7/7、工具 6/6、事实 9/9，无差异，端到端 15.077 秒。
- 报告保存 5 次模型调用的原始 usage：输入依次为 1476、4338、4572、4899、6028 token，输出为 49、170、240、1024、919 token；合计输入 21313、输出 2402、provider total 23715，缺失用量 0。按保守峰值单价 `21313×0.3/1000000 + 2402×1.2/1000000 = 0.0092763 USD`，与报告已核实费用一致；费用不可核实预留为 0。两次真实冒烟的应用侧费用估值合计 0.0187593 USD，低于先前用户批准的 2 USD 上限；最终账单、隐藏重试或计费口径仍需在 provider 侧核对。
- 重验后仅移除专用 `chainlens-m9-full` 容器及网络；原演示环境未动。此报告是在**未提交的工作区**产生，`gitCommit` 仅指向当时 HEAD `8d59a375...`，不代表该 commit 单独包含 Full 实现；发布前须提交实现并重新生成受控报告，避免将当前临时报告误称为可从该 commit 完整复现。

## M9 工程验收核对（当前为分阶段记录，不等同于 M9 全部通过）

| 验收域 | OpenSpec 项 | 实际证据 | 当前结论 |
|---|---|---|---|
| Offline + CI | 5.1–5.2、6.1–6.5 | 本地 30 条解析 585/585、规则 210/210；远端 CI 三 job 全绿并上传 `transaction-offline-evaluation` artifact；故障注入非零退出 | 已通过 |
| 发布材料与本地启动 | 5.3–5.6、8.3 | README、架构图、示例报告、截图及 M5 干净快照演练；链接和数字核对记录见上节 | 已通过本地工程检查 |
| Full 单笔 DeepSeek | 7.7 的小子集部分 | 真实 Anvil 冒烟和修复后的 2020 历史交易冒烟均通过；逐次 token、单价、预算和六指标已保存 | 小子集执行通过 |
| Full 全 30 条 | 7.7 后半 | 两轮真实 30 条报告均已落盘；首轮 10/30，修复后 29/30；六指标、失败明细和费用见下节 | 7.7 执行与记录完成，但第二轮整体 `passed=false`，不能称质量门槛全绿 |
| 本地 provider 替换 | 7.8 | Ollama 当前只有 embedding 模型；聊天模型下载中断，尚无同子集对照报告 | 未验收 |
| 学习验收 | 8.6 | 维护者尚未独立解释、复现、修改和排错；自动测试不能代替 | 未验收 |

上述状态意味着 8.5 的验收**记录已建立**，但 M9 完整工程验收尚未达成；7.7 已完成真实执行和失败记录，7.8、8.5、8.6 仍未勾选，不申请同步主 spec 或归档。

## 继续推进的前置探测（2026-10-19）

- 在最新未提交 Full 实现上重新运行离线 CI 等价门槛：`TransactionOfflineEvaluationGateTest` 3/3 通过，7 个 Reactor 模块成功；正式 30 条结果仍为解析 585/585、规则 210/210。此次未启动模型或数据库容器。
- [dRPC 官方 Ethereum 文档](https://drpc.org/docs/ethereum-api)公布无需本项目密钥的公开端点 `https://eth.drpc.org/`。只读调用确认 chainId=1，且对评测集 15 条历史样本逐一按原交易 `to` 和 `blockNumber` 查询 `eth_getCode`：14 条返回实际代码或明确的 `0x`（EOA），1 条 `to=null` 的合约创建无需该查询；未见错误响应。2020 和 2025 两端样本均返回历史代码。这只验证历史代码读取前提，不代表 30 条 transaction、receipt、block 取证或真实模型评分已经通过；完整 Full 仍要单独验收。
- [Ollama 官方 Qwen3 模型页](https://ollama.com/library/qwen3)将 `qwen3:0.6b` 标为支持工具调用，大小约 523 MB。首次拉取到约 35% 时失败；续传重新显示约 19% 并长期不前进，已主动中断。`ollama list` 仍只有 `qwen3-embedding:0.6b`，不能把 embedding 模型当聊天模型，因此 7.8 未验收，也未修改评测代码绕过本地 provider 要求。
- 新增 [`docs/M9-学习验收.md`](../../../docs/M9-学习验收.md)并从 README 链接，明确维护者要亲自解释、复现 fixture 与评分、排查 CI/预算失败、修改安全合成测试。该文档只是验收准备，不是 8.6 的完成证据。
- 用户随后明确批准以新的 2 USD 为本轮累计上限执行 7.7；本轮结果与费用见下节。先前两次冒烟的应用侧合计费用估值 0.0187593 USD 不计入这次新批准的 2 USD。

## 7.7 真实 DeepSeek 30 条运行与缺陷复核（2026-10-19）

- 执行前重新核对 [DeepSeek 官方模型与价格页](https://api-docs.deepseek.com/quick_start/pricing/)：使用 `deepseek-flash`、1M 输入上界、保守峰值 cache-miss 输入 $0.3/百万 token 与输出 $1.2/百万 token。只读主网 RPC `https://eth.drpc.org/` 按 [dRPC Ethereum 文档](https://drpc.org/docs/ethereum-api)选用；独立 `chainlens-m9-full` Anvil 在 18545 启动、seed 退出 0，固定 Anvil 交易哈希可查。评测报告均为本地忽略提交产物，没有把密钥或 RPC 凭据写入仓库。
- 第一轮 `eval/results/full/deepseek-30-20261019/full.json`/`full.md`：30/30 执行、10/30 通过，`passed=false`，解析 515/515、规则 175/175、工具 133/133、已检查事实 92/92；136 次模型调用的 provider usage 完整，输入 1,146,414、输出 80,544 token，费用估值 $0.4405770，无不可核实预留或预算停止。1024 输出上限叠加模型默认思考模式频繁截断 JSON，导致大量模板降级；五条 2020–2021 历史用例还出现 `ToolExecutionException`。这些分母只包含成功完成相应阶段的样本，不能据此声称 30 条全通过。
- 追查确定性缺陷：伦敦升级前区块 `baseFeePerGas=null`，`AgentTools.getBlock()` 曾使用禁止 null 的 `Map.of`；异常兜底又用 `Map.of("error", e.getMessage())`，在消息为 null 时二次抛错。现用可保存 JSON null 的有序 Map，工具错误仅返回稳定异常类型；新增旧区块回归测试。`mvn -B -ntp verify` 在允许本机端口与 Docker 的环境通过，10 个 Reactor 模块全部成功，耗时 30.580 秒；普通沙箱首次因 WireMock 无权绑定临时端口失败，并非代码失败。
- 参考 [Spring AI DeepSeek 配置文档](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html)，仅在验证命令设置 `spring.ai.deepseek.chat.thinking.type=disabled`，并将输出上限设为 2048，不改变默认产品配置。选取旧区块 `historical-2020-approval` 的修复冒烟 `eval/results/full/deepseek-historical-smoke-20261019/full.json` 为 1/1 通过：工具 4/4、事实 17/17、费用估值 $0.0073329。
- 第二轮 `eval/results/full/deepseek-30-retest-20261019/full.json`/`full.md`：30/30 执行、29/30 通过，`passed=false`；解析 585/585、规则 210/210、工具 129/129、已检查事实 285/285；112 次模型调用的 usage 完整，输入 819,248、输出 48,951 token；端到端 P50 9.268 秒、P95 14.601 秒。费用估值 $0.3045156，预算闸门为本轮单独配置 $1.55，未停止、不可核实预留为 0。唯一失败 `historical-2022-eth` 在结构化报告两次校验后降级为模板，导致该例事实 token 分母为零；不能将聚合 285/285 误解为该例通过。
- 本轮三个真实运行（首轮 30、旧区块冒烟、复跑 30）的应用侧费用估值合计 **$0.7524255 < $2**。这是按 provider 返回 token usage 和所填保守峰值单价算出的估值，不是 DeepSeek 最终账单；隐藏重试和计费口径差异仍属限制。两份 30 条报告如实保留失败，未删用例、放宽阈值或伪造全绿。独立 Anvil/seed 容器与网络已移除，原演示环境未动。报告生成时工作区尚有未提交 Full 实现，因此其中的 `gitCommit` 仅指向当时 HEAD，不能将该 commit 单独当作复现版本。
- 按任务原文，7.7 的“真实小子集 + 30 条执行 + 六指标及失败记录”已经完成，故勾选；第二轮整体质量结果仍为失败。7.8 provider 替换、8.5 最终工程验收及 8.6 维护者学习验收仍未完成，本轮不申请同步主 spec 或归档。

## 8.5 工程验收记录（2026-10-19）

- 完整中文验收记录见 [`docs/M9-工程验收.md`](../../../docs/M9-工程验收.md)，按 Offline+CI、Full、发布材料、7.8 provider 替换和学习验收分域列出状态与证据。
- 7.8 的“通过”是维护者本轮明确作出的状态决定；当前没有 Ollama 聊天模型对照报告，故验收记录同时标注“用户确认状态，证据待补”，不把它写成已经完成的机器运行。
- 8.6 保持未完成；工程测试和文档不能代替维护者独立解释、复现、修改和排错。

## 7.8 本地 Ollama 同交易对照（2026-10-19）

- 维护者已下载 `qwen3:4b`；本机 `ollama list` 显示模型大小 2.5 GB，`ollama show qwen3:4b` 显示 `tools` 能力和 262,144 token 模型上下文。按 [Spring AI Ollama 文档](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)使用 `spring.ai.model.chat=ollama`、`spring.ai.ollama.chat.model`、`num-ctx`、`num-predict` 与 `think` 配置。独立 `chainlens-m9-ollama` Anvil 在 18545 造数，seed 退出 0；同一 `anvil-finite-approval` 交易哈希与 DeepSeek 冒烟相同。
- 初次本地运行使用 2048 输出上限，模型首次响应正好耗尽 2048 token，`ToolExecutionException` 使报告失败。把上限提高到 4096 后，安全诊断日志定位为 `searchKnowledge` 缺失 `query`，原 `Map.of` 不接受 null，底层抛 `NullPointerException`。通用 `AgentTools` 现对三种带字符串参数的工具先保存可空参数、再于工具内部检查；缺参产生失败工具步骤和稳定错误，而不会中断整笔 Full。新增 `AgentToolsKnowledgeTest` 回归用例，定向运行 4/4 通过；评测执行器与规则业务代码没有按 provider 分叉。
- 最终本地忽略提交的 `eval/results/full/ollama-qwen3-4b-after-guard-20261019/full.json` 与 `full.md`：1/1 用例通过，解析 15/15，规则 7/7，工具 4/4，事实 1/1，模型调用 2 次，provider usage 输入 12,350、输出 3,310 token，端到端 136.063 秒，API 费用估值 0 USD。模型上下文实际设为 16,384，输出上限 4,096，关闭思考模式；本机 GPU 推理期间有连接池线程饥饿警告，结果文件完整并正常退出。
- 使用 `jq` 逐字段对照 `eval/results/full/deepseek-usage-acceptance/full.json`：两份报告的 `datasetVersion=v1`、`caseId`、`txHash`、`ruleVersion=7-rules-defaults-v1`、解析 15/15 和规则 7/7 完全相同。DeepSeek 的工具 6/6、事实 9/9、5 次调用、端到端 15.077 秒、费用估值 $0.0092763；Ollama 的工具 4/4、事实 1/1、2 次调用、端到端 136.063 秒、API 费用估值 0。事实分母不同，不能将两个 100% 当作报告内容等价。可读对照见 [`docs/M9-Ollama-对照.md`](../../../docs/M9-Ollama-对照.md)。
- 7.8 的同子集真实对照及差异记录已完成，故勾选。8.6 学习验收仍独立未完成；Full 30 条 DeepSeek 曾有 1 条降级，不能因本次单例通过改写历史结果。
- 修复后完整 `mvn -B -ntp verify` 通过，10 个 Reactor 模块均成功，耗时 28.052 秒。`chainlens-m9-ollama` 专用 Anvil/seed 容器和网络已移除；本地 JSON/Markdown 报告保留在被忽略的 `eval/results/full/`，原演示环境未动。


- `914c967 feat(mcp): add read-only Streamable HTTP and stdio server`：归档 M8 工程文件，未混入 M9 评测实现。
- `ab34f84 feat(evaluation): add offline transaction scoring and CI gate`：M9 数据集、离线评分、规则证据修正、离线 CI job 与 OpenSpec 计划。
- `865071b docs(evaluation): publish offline baseline reports`：提交 README 所链接的机器报告和可读报告，并修正忽略规则。
- `92eacb2 docs(openspec): record M9 offline verification state`：记录验收状态、提交哈希及远端前置条件。
- `git diff --check 0130953..HEAD` 通过；本地分支工作区干净，秘密扫描未命中；提交列表未混入 `.env`、模型缓存或 full 模式临时产物。M8 实现只在首个 MCP 提交，之后三笔提交针对 M9 评测与验收记录。
- 分支为 `codex/m9-evaluation-release`；四个提交均只在本地，尚未 push。

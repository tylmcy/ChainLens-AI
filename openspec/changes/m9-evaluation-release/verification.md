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

## 集中审查清单（8.2，未完成）

| 检查项 | 已核对的证据 | 状态 |
|---|---|---|
| 30 条 ground truth 与原始 RPC 定位 | `FrozenDatasetIntegrityTest` 逐项核对哈希、回执、事件字段和 JSON Pointer；另有人工逐条回执复核记录 | 通过 |
| 指标分母和阈值 | `latest.json` 为 585 个解析字段、210 个规则判断；`RuleComparatorTest` 覆盖整数阈值边界，不使用显示舍入放宽门槛 | 通过 |
| 规则证据位置 | `RiskAddressHitRuleTest` 等测试覆盖 tx 地址和事件 topic，缺失日志时不得编造证据 | 通过 |
| 秘密处理 | fixture、案例清单、示例报告与截图无密钥扫描命中；clean snapshot 未带入 `.env` | 通过 |
| 报告稳定性 | `EvaluationReportWriterTest` 覆盖排序、双格式同源和原子写入；离线 JSON/Markdown 数字一致 | 通过 |
| 预算硬停止 | Full runner 与预算功能尚未实施，暂无可审查实现 | 待 7.1–7.6 |
| M5 / CI 兼容性 | 本地 M5 快照演练通过；Actions workflow 结构与本地 Maven 等价命令已检查，但 GitHub runner 和 artifact 未实际验收 | 部分通过 |

## 远端 CI 前置条件

- 已将用户提供的 `https://github.com/tylmcy/ChainLens-AI.git` 配置为 `origin`，只读查询确认远端当前没有分支。`gh auth status` 显示 GitHub CLI token 无效，因此还无法读取或触发 Actions run，也无法取回 artifact。
- 用户的项目约束禁止代理向远程仓库 push。该约束仍生效；在真实 Actions artifact 可供检查之前，6.5 保持未完成，Full 阶段 7.x 按设计不启动。

## 本地提交记录

- `914c967 feat(mcp): add read-only Streamable HTTP and stdio server`：归档 M8 工程文件，未混入 M9 评测实现。
- `ab34f84 feat(evaluation): add offline transaction scoring and CI gate`：M9 数据集、离线评分、规则证据修正、离线 CI job 与 OpenSpec 计划。
- `865071b docs(evaluation): publish offline baseline reports`：提交 README 所链接的机器报告和可读报告，并修正忽略规则。
- 分支为 `codex/m9-evaluation-release`；三个提交均只在本地，尚未 push。

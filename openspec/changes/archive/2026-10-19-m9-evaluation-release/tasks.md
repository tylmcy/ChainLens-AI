# Tasks

## 1. 批次 A：基线与公共契约（`gpt-5.6-sol`，中等强度）

- [x] 1.1 核对 M8 归档、主 spec 与当前未提交文件，把 M8 和 M9 的改动边界记录清楚；通过 `git status --short` 和 `openspec list --json` 验证没有把 M8 实现误算成 M9
- [x] 1.2 运行 M9 开始前的 Maven 基线测试并保存通过数或既有失败清单；通过同一条命令可重复得到结果，确认后续失败可归因于 M9
- [x] 1.3 在 `chainlens-evaluation` 定义数据集、ground truth、fixture 清单、差异和指标结果的 Java 类型与 JSON Schema；通过序列化往返测试及必填字段测试验证公共契约
- [x] 1.4 实现数据集加载和全量预检，校验 30 条总数、15/15 来源、唯一 `caseId`、交易哈希、网络、ground truth、fixture 和来源元数据；通过合法/缺项/重复/数量错误用例测试验证失败信息包含 `caseId`
- [x] 1.5 实现 `FixtureEthRpcClient`，从单用例目录回放 chainId、transaction、receipt、block 和 code，并检查交易哈希一致性；通过完整回放、缺文件、错误哈希和 code 不可用测试验证不联网且不伪造

## 2. 批次 A：Offline 评测内核（`gpt-5.6-sol`，中等强度）

- [x] 2.1 建立评测专用的七规则装配入口并复用生产 `TxFactsAssembler` 与 `RuleEngine`；通过代表性 fixture 测试验证实际 parser/规则类被调用而非评测副本
- [x] 2.2 实现状态、ETH 变化和 calldata 三态的逐字段比较器，支持 `applicable:false` 且禁止缺失适用字段退出分母；通过正确、错误、不适用和缺失字段测试核对分子分母
- [x] 2.3 实现 Transfer/Approval 事件的无序多重集比较和稳定字段路径；通过乱序、重复、漏项和多项事件测试验证计数与差异定位
- [x] 2.4 实现 30×7 规则分类器与 TP/TN/FP/FN 聚合，同时校验 finding 的 `ruleId`、`confidence`、`evidence` 和原始位置；通过四种分类及结构缺失测试验证规则准确率口径
- [x] 2.5 实现整数阈值判断：解析准确率至少 98%、规则准确率等于 100%；通过 98% 边界、低于边界和浮点显示舍入测试验证不会误放行
- [x] 2.6 实现 offline 单用例执行、批量聚合与明确异常结果；通过同一 fixture 重复运行测试验证确定性，且 RPC/DB/LLM 环境变量为空时仍能完成
- [x] 2.7 实现稳定的 JSON 结果 Schema 和由同一结果对象生成的 Markdown 渲染器，使用原子写入并记录 Git commit、数据集/规则版本与差异证据；通过快照测试、排序测试和 commit 不可用测试验证两种报告一致
- [x] 2.7a 修正 `RISK-ADDRESS-HIT` 的证据定位，使原始指针指向实际命中的 tx.from/tx.to 或事件 topic；通过收款地址和事件参与方测试验证，缺失原始日志时不得伪造指针
- [x] 2.8 提供 offline 命令入口及 CI 门槛测试适配器，并为公开方法补充说明作用和边界的简体中文注释；通过本地命令的成功退出、故意失败的非零退出和生成双格式文件验证

## 3. 批次 B：固定 Anvil 数据（`gpt-5.6-luna`，中等强度，连续处理）

- [x] 3.1 按已冻结 Schema 将现有 7 个 Anvil 风险场景转成 fixture 与 ground truth；逐条用原始字段/日志索引人工复核并通过数据集局部校验
- [x] 3.2 补充 8 个 Anvil 反例、边界值、多事件和 calldata 三态场景，保持公开测试账户与虚拟资产；通过造数脚本重复运行验证交易顺序、地址与场景清单可复现
- [x] 3.3 完成 15 条 Anvil 用例的期望解析字段和七规则命中矩阵；通过 offline 执行验证无漏标/误标，若需要改公共 Schema 或生产 parser 则停止本批次并交回 `gpt-5.6-sol`

## 4. 批次 B：固定公开历史数据（`gpt-5.6-luna`，中等强度，连续处理）

- [x] 4.1 选择 15 条覆盖成功/失败、legacy/EIP-1559、ETH、Transfer、Approval、calldata 三态和 EOA/合约组合的公开 Ethereum 历史交易；通过覆盖矩阵验证不是 15 条同质样本
- [x] 4.2 使用只读 RPC 抓取并冻结每条交易的 transaction、receipt、block、chainId 和适用的历史 code 响应，密钥仅从环境变量读取；通过秘密扫描和 fixture 哈希交叉校验验证没有提交凭据
- [x] 4.3 为 15 条公开用例填写来源、采集日期、网络、交易哈希、自撰标注许可和完整 ground truth；逐条以原始字段/日志位置复核并通过数据集全量校验
- [x] 4.4 合并 15 条 Anvil 与 15 条公开历史用例为 `transaction-eval-v1`；通过加载器验证恰好 30 条、来源比例正确、七规则正反例和解析类别覆盖达到清单要求

## 5. 批次 B：Offline 结果与发布材料（`gpt-5.6-luna`，中等强度，连续处理）

- [x] 5.1 对完整 30 条数据运行 offline 评测并生成 `latest.json` 与 `latest.md`；验证解析准确率不低于 98%、规则准确率为 100%，且每个差异都能定位 `caseId`
- [x] 5.2 故意篡改一项 ground truth 和一项 expected rule 后运行临时副本；验证解析与规则两种失败都会非零退出并输出期望值、实际值和 TP/TN/FP/FN 分类，随后恢复受控数据
- [x] 5.3 更新 README 的项目定位、证据边界、架构、环境变量、Compose/Anvil 启动、测试、offline/full 评测、故障排查和免责声明；由另一人或干净终端逐条照做验证命令无缺步
- [x] 5.4 整理架构图源文件与导出图片，明确 JSON-RPC→解析→规则→Agent→报告以及 offline 旁路；通过 README 链接和图片可读性检查验证
- [x] 5.5 保存不含秘密和本机隐私路径的实际演示截图与 Anvil 示例报告，报告包含 `evidence`、`ruleId`、`confidence` 和原始定位；通过人工检查及秘密扫描验证
- [x] 5.6 在 Apple Silicon MacBook Air M5 上从干净 checkout 进行约 10 分钟启动演练，记录前置条件、耗时、命令和最终报告；验证无需宿主机安装 Foundry、Node、PostgreSQL 或真实密钥

## 6. 批次 C：优先落地 CI 门槛（`gpt-5.6-sol`，中等强度）

- [x] 6.1 在 GitHub Actions 增加独立 `transaction-offline-evaluation` job，精确运行门槛测试且 `always()` 上传报告；通过 workflow 语法检查和本地等价 Maven 命令验证
- [x] 6.2 清空 RPC、数据库和模型环境变量运行 CI 等价命令；验证 30 条用例仍全绿、日志没有外部连接、无需 Docker 且不产生模型费用
- [x] 6.3 在临时分支或本地工作流副本制造低于门槛的结果；验证 job 失败而 artifact 仍生成，随后撤销故障注入并确认正式配置全绿
- [x] 6.4 检查 offline job 与既有 `unit-and-package`、RAG job 的职责和 Maven参数，避免重复跑 DB 测试或意外放开 `smoke`；通过 CI 日志中的测试选择与 tag 结果验证
- [x] 6.5 完成 offline+CI 第一阶段用户可见验收：展示 30 条结果、一次故障门槛和 CI artifact；只有这些证据齐全后才进入 full 模式

## 7. 批次 C：Full 六指标与预算控制（`gpt-5.6-sol`，中等强度）

- [x] 7.1 将事实一致性校验提取为可计数评分结果，并保持原生产 `validate` 行为不变；通过现有 Agent 测试和新增合法/幻觉/零 token 测试验证无回归
- [x] 7.2 增加 provider-neutral 的 Agent 遥测回调，采集模型 token 和 RPC/解析/规则/LLM 分段耗时，默认实现为空；通过 mock ChatModel 测试验证正常、usage 缺失和异常路径
- [x] 7.3 实现 full runner 对 `AnalysisService` 的批量调用，统计工具成功率、事实一致率、端到端及分段 P50/P95；通过 mock 结果测试验证空工具、失败工具和 percentile 小样本语义
- [x] 7.4 实现输入/输出 token 单价、来源日期、逐笔成本、总成本和硬预算中止；通过恰好触顶、超限前停止、usage 不可用测试验证不会继续发起模型调用或猜测费用
- [x] 7.5 在 `chainlens-app` 的显式 full profile 装配 runner，保持评测代码只依赖 `ChatModel`/`AnalysisService` 抽象；通过依赖检查和 DeepSeek mock 配置启动测试验证没有新增厂商耦合
- [x] 7.6 新增仅 `workflow_dispatch` 触发的 full workflow，凭据使用 CI secrets、预算为必填输入并始终上传结果；通过 workflow 事件检查验证普通 push/PR 不触发付费任务且日志不泄密
- [x] 7.7 先用小子集运行真实 DeepSeek full 冒烟，再在预算允许时运行 30 条 full 评测；验证报告包含全部六指标、模型/provider/单价/预算和失败明细（复跑 30 条中 29 条通过、1 条因报告校验降级；执行与失败记录已完成，不代表全绿）
- [x] 7.8 使用 Ollama 本地模型对同一小子集执行 provider 替换验收；比较数据集版本与确定性事实结果并记录模型差异，验证业务评测代码无需修改（`qwen3:4b` 对 `anvil-finite-approval` 的 Full 单例通过；对照见 `docs/M9-Ollama-对照.md`）

## 8. 批次 C：集中审查、测试与交付（`gpt-5.6-sol`，中等强度）

- [x] 8.1 运行 `mvn -B -ntp verify`、offline 门槛命令、RAG 评测和相关 Web 测试，记录通过数、耗时与任何环境前提；验证 M0-M8 能力无回归
- [x] 8.2 集中审查 30 条 ground truth、指标分母、阈值、证据位置、秘密处理、预算中止、Apple Silicon/CI 兼容性和报告排序；通过审查清单逐项给出文件或测试证据
- [x] 8.3 对照 JSON 结果核验 README 评测数字、架构图、截图、示例报告和链接；验证没有手填不一致数字、失效路径、TODO、TBD 或范围冲突
- [x] 8.4 按功能拆分 Conventional Commits，并用 `git diff --check`、`git status --short` 和提交历史验证没有混入密钥、临时 full 产物或无关 M8 文件
- [x] 8.5 完成 M9 工程验收记录，区分 offline+CI、full、发布材料和未完成学习项；通过 OpenSpec checklist 与实际证据逐项对应后再申请同步主 spec 和归档（记录见 `docs/M9-工程验收.md`；7.8 用户确认与机器证据缺口、8.6 未完成均明确列出）
- [ ] 8.6 单独进行学习验收：维护者解释并独立复现一条 fixture、一次计分、一项 CI 故障和一次预算中止，再修改一条安全测试用例并排错；不得仅凭代码生成、测试通过或文档存在勾选完成

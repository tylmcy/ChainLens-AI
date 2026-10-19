# M9 本地 Ollama 与 DeepSeek 对照

验收日期：2026-10-19。使用同一套 `transaction-eval/v1` 数据集、规则版本 `7-rules-defaults-v1` 和 Anvil 用例 `anvil-finite-approval`。固定交易哈希为 `0x91c608e694505a132fc47f44da755abfe478724527fab69bc1ff023b3a13aae8`。

| 指标 | DeepSeek `deepseek-flash` | 本地 Ollama `qwen3:4b` |
|---|---:|---:|
| 单例执行 | 通过 | 通过 |
| 解析字段 | 15/15 | 15/15 |
| 七规则判断 | 7/7 | 7/7 |
| 工具调用 | 6/6 | 4/4 |
| 报告中被检查的事实 token | 9/9 | 1/1 |
| 模型调用次数 | 5 | 2 |
| 输入 / 输出 token | 21,313 / 2,402 | 12,350 / 3,310 |
| 端到端延迟 | 15.077 秒 | 136.063 秒 |
| 应用侧 API 费用估值 | 0.0092763 USD | 0 USD |

两份机器报告分别位于本地忽略提交的 `eval/results/full/deepseek-usage-acceptance/full.json` 和 `eval/results/full/ollama-qwen3-4b-after-guard-20261019/full.json`，同目录还各有 `full.md`。逐字段比较确认 `datasetVersion`、`caseId`、`txHash`、`ruleVersion`、解析计数和规则计数相等。Ollama 的事实校验样本只有 1 个 token，因此 `1/1` 不能解释成两家模型的报告信息量相同；两次延迟也受本机推理与不同输出上限影响。

Ollama 模型由本机 `ollama show qwen3:4b` 确认支持 `tools`，模型上下文上限为 262,144 token。本次实际请求上下文窗口为 16,384，输出上限为 4,096，关闭思考模式。DeepSeek 对照是此前真实冒烟，输出上限为 1,024。Ollama 的 API 单价配置为零，不代表本机算力、电力或时间没有成本。

首次 Ollama 执行因 `searchKnowledge` 缺失 `query` 而在工具参数记录处抛异常，诊断运行确认底层是 `NullPointerException`。通用 `AgentTools` 现在把缺失参数记录为失败工具步骤并返回稳定错误，不让整笔分析异常退出；`getContractCode` 与 `resolveSelector` 的同类边界也一并处理。`AgentToolsKnowledgeTest` 的缺参回归用例通过。最终对照用例工具 4/4 成功、无差异；评测执行器和规则计算代码没有按 provider 分叉。

复现时先运行 `ANVIL_PORT=18545 docker compose -p chainlens-m9-ollama --profile demo-offline up --build -d anvil demo-seed`，确认 seed 退出码为 0；再使用 `evaluation-full` profile，设置 `SPRING_AI_MODEL_CHAT=ollama`、`SPRING_AI_MODEL_EMBEDDING=none`、`SPRING_AI_OLLAMA_CHAT_MODEL=qwen3:4b`、`SPRING_AI_OLLAMA_CHAT_THINK=false`、`SPRING_AI_OLLAMA_CHAT_NUM_CTX=16384`、`CHAINLENS_EVAL_CASE_IDS=anvil-finite-approval`、`CHAINLENS_EVAL_ANVIL_RPC_URL=http://127.0.0.1:18545` 和 `CHAINLENS_EVAL_MAX_OUTPUT_TOKENS=4096`。Full 入口还要求 provider/model、单价日期、上下文来源、输入上限、输入/输出单价和正预算；本机 Ollama 可将两项 API 单价设为 0。

报告生成时 Full 实现仍有未提交文件，报告内的 `gitCommit` 只表示当时仓库 HEAD，不能单独作为复现此报告的代码版本。模型输出具有随机性，重跑后的工具次数和被检查事实数可能变化；确定性解析与规则分数应保持一致。

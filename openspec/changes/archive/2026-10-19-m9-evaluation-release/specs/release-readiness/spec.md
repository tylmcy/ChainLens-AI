# Spec Delta

## Purpose

定义 ChainLens V1 在持续集成与公开 GitHub 展示前必须通过的质量门槛、可重复启动验收和发布材料要求，使新用户能够快速验证项目而无需信任作者口头说明。

## ADDED Requirements

### Requirement: CI 强制执行 Offline 评测门槛
系统 MUST 在面向主分支的 push 与 pull request 中执行独立的 offline 交易评测任务；当解析准确率低于 98%、规则准确率低于 100%、数据集无效或执行异常时，该任务 MUST 失败并阻止 CI 全绿。任务 MUST 在任何结果下保存可取得的评测报告 artifact。

#### Scenario: 质量回退进入 Pull Request
- **WHEN** 代码改动导致任一 offline 指标未达到门槛
- **THEN** CI 评测任务失败，日志显示失败指标，artifact 提供逐用例差异用于排错

#### Scenario: CI 未配置外部凭据
- **WHEN** 默认 CI 没有 RPC URL、数据库密码或模型 API Key
- **THEN** offline 评测仍完成且不访问公网服务、不产生模型费用

### Requirement: Full 评测只能显式手动触发
系统 SHALL 提供手动触发的 full 评测工作流，并从环境变量或受保护的 CI secret 读取外部凭据和预算上限；该工作流 MUST NOT 成为每个 push 或 pull request 的默认必跑任务。

#### Scenario: 未授权的普通代码提交
- **WHEN** 开发者仅 push 或创建 pull request
- **THEN** CI 不启动付费模型评测，也不要求存在模型密钥

#### Scenario: 手动执行 Full 评测
- **WHEN** 有权限的操作者显式触发工作流并提供预算与所需 secret
- **THEN** 工作流执行 full 模式并保存完整结果 artifact，同时不在日志或产物中泄露 secret

### Requirement: 新用户可按文档复现 V1
README SHALL 用简体中文为主说明项目问题、证据边界、架构、技术亮点、前置条件、环境变量、Docker Compose 启动、Anvil 演示、测试与评测命令、常见故障和免责声明；经一次干净环境演练后，新用户在依赖已可下载且 Docker Desktop 已运行的条件下，SHALL 能在约 10 分钟内得到一份本地交易分析报告。

#### Scenario: Apple Silicon 冷启动演练
- **WHEN** 验收者在 Apple Silicon Mac、Java 21 和 Docker Desktop 环境按 README 从干净 checkout 操作
- **THEN** 所用镜像支持 arm64，演练无需 Foundry、Node、PostgreSQL 或真实密钥的宿主机安装，并产出可核验的 Anvil 分析报告

#### Scenario: 外部 RPC 或 LLM 未配置
- **WHEN** 验收者只运行默认本地演示且没有配置外部 RPC 或模型 Key
- **THEN** README 说明并展示确定性降级路径，解析、规则与报告仍可验证且不会伪造外部结果

### Requirement: GitHub 首页展示可核验的发布证据
公开仓库 SHALL 提供可读架构图、实际演示截图、包含 `evidence`、`ruleId`、`confidence` 和原始数据定位的示例报告，以及与当前数据集版本匹配的最新评测摘要；README MUST 在一分钟内让读者找到问题定义、架构、技术亮点、启动方式和评测结果。

#### Scenario: 招聘者快速浏览仓库
- **WHEN** 读者仅访问仓库首页且不运行代码
- **THEN** 一分钟内可看到项目解决的问题、核心证据链、主要技术、启动入口和最新 offline 指标，并可打开对应架构图、截图、示例报告和完整评测结果

#### Scenario: 展示材料与当前版本不一致
- **WHEN** 评测摘要引用的数据集版本、阈值或指标与机器可读结果不一致
- **THEN** 发布验收失败，且不得用手工修改的更好数字替代执行器产物

### Requirement: 发布验收保留机器验证与学习验收的区别
M9 的工程完成 SHALL 以自动测试、offline CI 门槛、full 报表和启动演练证据为准；学习验收 MUST 另外确认维护者能够解释、独立复现、修改和排查数据集、指标与 CI 失败，不能只因代码生成或测试通过而自动标记完成。

#### Scenario: 工程测试通过但尚未口头或独立复现验收
- **WHEN** 所有自动任务已通过但维护者尚未完成学习验收
- **THEN** M9 可记录工程交付完成，但学习验收保持未完成并列出待复现与讲解项

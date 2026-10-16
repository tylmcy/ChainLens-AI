# Spec Delta

## Purpose

定义在 Apple Silicon Mac 上无需真实资产、外部 RPC 或 LLM Key 即可复现 ChainLens 核心分析能力的本地演示环境。

## ADDED Requirements

### Requirement: 一条命令启动离线演示
系统 SHALL 通过 Docker Compose 启动 PostgreSQL、Anvil、演示数据生成器和 ChainLens 应用，并支持覆盖宿主机端口。

#### Scenario: Apple Silicon 首次启动
- **WHEN** 用户在已启动 Docker Desktop 的 MacBook Air M5 上运行演示启动脚本
- **THEN** 所有日常运行镜像兼容 arm64，且无需宿主机安装 Java、PostgreSQL、Foundry 或 Node

#### Scenario: 默认应用端口被占用
- **WHEN** 用户通过环境变量指定其他 ChainLens 端口
- **THEN** 演示使用指定端口启动，不要求停止无关应用

### Requirement: 生成确定性的七类本地交易
演示环境 SHALL 重置 Anvil 内存链并生成分别覆盖七条风险规则的交易清单，交易只使用 Anvil 公开测试账户和虚拟资产。

#### Scenario: 重复生成演示数据
- **WHEN** 用户在相同版本的合约字节码和脚本上重新运行造数
- **THEN** 系统生成可复现的合约地址、交易顺序和场景清单

### Requirement: 自动验收每条演示规则
演示脚本 MUST 将每个交易哈希提交给 ChainLens，并检查报告中是否包含对应的预期 ruleId。

#### Scenario: 七个场景全部命中
- **WHEN** 应用、数据库和 Anvil 均健康且七笔场景交易已生成
- **THEN** 验收脚本明确报告七条规则全部通过

#### Scenario: 任一场景未命中
- **WHEN** 任一分析结果缺少场景声明的预期 ruleId
- **THEN** 验收脚本以非零状态结束并指出失败场景

### Requirement: 本地演示不包含真实秘密
仓库和演示清单 MUST NOT 包含真实私钥、助记词、真实资产凭据或已填写的模型 API Key。

#### Scenario: 检查演示配置
- **WHEN** 用户审查 Compose、脚本和环境变量示例
- **THEN** 只能看到公开测试地址、虚拟资产和空的秘密变量占位

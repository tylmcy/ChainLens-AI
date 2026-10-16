# ethereum-transaction-evidence Specification

## Purpose

定义 ChainLens 如何只依赖 Ethereum JSON-RPC 获取、解析并定位一笔交易的链上事实，使后续规则和报告能够追溯到原始响应。

## Requirements

### Requirement: 按交易哈希完成链上取证
系统 SHALL 校验输入为 32 字节 Ethereum 交易哈希，并从配置的同一条链获取 transaction、receipt 和 block；系统不得使用 LLM 补齐缺失事实。

#### Scenario: 成功取得完整交易事实
- **WHEN** 用户提交格式正确且节点可查询到的交易哈希
- **THEN** 系统返回包含交易、回执、区块、链 ID 和交易状态的事实对象

#### Scenario: RPC 数据不可用
- **WHEN** 节点不可达、响应错误或交易不存在
- **THEN** 系统返回明确的取证错误且不生成伪造分析结果

### Requirement: 解析交易动作和事件
系统 SHALL 从原始交易和回执解析 ETH 变化、ERC-20 Transfer、ERC-20 Approval，并尝试用 ABI 或 4-byte selector 解释 calldata；无法唯一确定的方法必须保留为未知或候选结果。

#### Scenario: 回执包含 ERC-20 日志
- **WHEN** 回执日志主题符合 Transfer 或 Approval 事件签名
- **THEN** 系统输出合约地址、相关地址、原始数值和对应日志位置

#### Scenario: 方法选择器未知
- **WHEN** calldata 包含无法由本地知识解析的 4-byte selector
- **THEN** 系统保留原始 selector 并明确标记为未知，而不是猜测函数名称

### Requirement: 证据可定位到原始数据
系统 MUST 为可用于重要结论的事实保留交易哈希及原始 JSON-RPC 定位信息，包括字段路径、日志索引或等价的可复核位置。

#### Scenario: 下游引用一项链上事实
- **WHEN** 规则或报告引用交易状态、数值、地址或事件
- **THEN** 对应证据能够定位到该交易的原始字段或日志索引

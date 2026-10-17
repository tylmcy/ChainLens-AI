---
id: ethereum-terms
title: Ethereum 交易分析常用术语
category: glossary
ruleIds: []
version: 1.0
---
## 交易、回执与区块

transaction 是用户提交的意图，包含 from、to、value、input、nonce 和 gas 等字段；receipt 是执行后的结果，包含 status、gasUsed 和 logs；block 提供交易被打包时的高度、时间与费用上下文。分析时三者缺一不可：只有交易不能确认执行成功，只有回执不便理解输入，使用最新区块代替历史区块又可能引入时间错位。ChainLens 会保留对应 JSON-RPC 方法与 JSON Pointer 作为证据位置。

## ETH、Wei 与 gas

Wei 是 ETH 的最小常用整数单位，链上 RPC 通常以十六进制数量返回。value 表示交易主动附带的原生资产，gasUsed 乘 effectiveGasPrice 得到实际执行费用；二者含义不同。合约内部发生的 ETH 转移通常需要 trace 才能完整观察，普通交易和回执不足以恢复全部内部调用。V1 因此只报告能从标准 RPC 确定的变化，并把缺失能力写入限制。

## 事件、calldata 与 ABI

事件日志由合约执行产生，topic 用于标识事件和索引参数，data 保存非索引参数。calldata 是发送给目标地址的输入字节；ABI 描述如何把这些字节解释成函数和参数。ERC-20 Transfer 与 Approval 有标准事件签名，仍需严格检查 topic 数量、数据长度和地址格式。解析失败时应保留原始值并明确标注未知，不能让语言模型根据上下文补齐缺失字段。

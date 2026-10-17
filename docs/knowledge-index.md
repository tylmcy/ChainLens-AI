# ChainLens 受控知识清单

此表供代码评审与人工抽查使用。运行时只读取 `docs/knowledge/*.md`，不会抓取外部网页。

| source | title | ruleIds |
|---|---|---|
| `docs/knowledge/erc20-unlimited-approval.md` | ERC-20 无限额度授权 | `ERC20-UNLIMITED-APPROVAL` |
| `docs/knowledge/failed-transaction.md` | Ethereum 交易执行失败 | `TX-FAILED` |
| `docs/knowledge/transfer-to-zero.md` | 向零地址转账与代币销毁 | `TRANSFER-TO-ZERO-ADDRESS` |
| `docs/knowledge/interaction-with-eoa.md` | 带 calldata 与无代码地址交互 | `INTERACTION-WITH-EOA` |
| `docs/knowledge/unknown-selector.md` | 未知方法选择器 | `UNKNOWN-SELECTOR` |
| `docs/knowledge/many-token-transfers.md` | 单笔交易包含大量 Token 转移 | `MANY-TOKEN-TRANSFERS` |
| `docs/knowledge/risk-address-hit.md` | 风险地址列表命中 | `RISK-ADDRESS-HIT` |
| `docs/knowledge/ethereum-terms.md` | Ethereum 交易分析常用术语 | 无（术语） |
| `docs/knowledge/security-basics.md` | 钱包与 DeFi 安全核对基础 | 无（通用安全提示） |

当前确定性切块测试要求总片段数保持在 20～40；内容或切块规则变化后由 `KnowledgeCorpusTest` 自动复核。

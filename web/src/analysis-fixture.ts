import type { AnalysisResult } from './types';

/** 与 Java AnalysisResult/TxFacts/Evidence record 的序列化字段一致的测试案卷。 */
export const analysisFixture: AnalysisResult = {
  analysisId: 'demo-analysis-1',
  txHash: `0x${'a'.repeat(64)}`,
  network: 'ANVIL',
  report: {
    summary: '检测到一笔 ERC-20 无限额度授权。',
    status: 'SUCCESS',
    actions: ['授权代币支出'],
    findings: [{
      ruleId: 'ERC20-UNLIMITED-APPROVAL', severity: 'HIGH', confidence: 1,
      title: '检测到无限额度授权', description: '授权额度为最大 uint256 值。',
      evidence: {
        chainId: 31337, network: 'anvil-local', blockNumber: 1,
        transactionHash: `0x${'a'.repeat(64)}`, logIndex: 0,
        rpcMethod: 'eth_getTransactionReceipt', fetchedAt: '2026-10-16T00:00:00Z',
        rawJsonRef: { rpcMethod: 'eth_getTransactionReceipt', jsonPointer: '/result/logs/0/data' },
      },
    }],
    sources: [{ type: 'CHAIN', label: '交易回执', reference: 'eth_getTransactionReceipt /logs/0' }],
    limitations: ['不构成投资建议，也不是完整的安全审计'],
  },
  facts: {
    network: 'ANVIL',
    transaction: { hash: `0x${'a'.repeat(64)}`, from: `0x${'1'.repeat(40)}`, to: `0x${'2'.repeat(40)}`, value: 0, input: '0x095ea7b3', blockNumber: 1, rawJson: '{}' },
    receipt: { status: '0x1', gasUsed: 45000, logs: [], rawJson: '{"logs":[{"data":"0xffff"}]}' },
    block: { number: 1, timestamp: 1760572800, rawJson: '{}' },
    gasCostWei: 45000, valueWei: 0, fromNetWei: -45000, toNetWei: 0,
    transfers: [], approvals: [{ tokenAddress: `0x${'2'.repeat(40)}`, owner: `0x${'1'.repeat(40)}`, spender: `0x${'3'.repeat(40)}`, valueWei: '115792089237316195423570985008687907853269984665640564039457584007913129639935', logIndex: 0 }],
    unknownLogTopics: [],
    calldata: { hasInput: true, selector: '0x095ea7b3', candidates: ['approve(address,uint256)'], resolvedSignature: 'approve(address,uint256)', status: 'RESOLVED' },
    contractCode: { address: `0x${'2'.repeat(40)}`, available: true, hasCode: true, codeSize: 10, queriedAtBlock: 1, unavailableReason: null },
    fetchedAt: '2026-10-16T00:00:00Z',
  },
  findings: [],
  toolSteps: [{ toolName: 'getTransaction', arguments: '{}', startedAt: '2026-10-16T00:00:00Z', durationMs: 10, success: true, summary: '已查询交易' }],
  llmUsed: false, degraded: true, persisted: true, totalDurationMs: 100, createdAt: '2026-10-16T00:00:00Z',
};

// 后端顶层 findings 与报告 findings 是同一组确定性规则结果。
analysisFixture.findings = analysisFixture.report.findings;

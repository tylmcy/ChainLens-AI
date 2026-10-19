#!/usr/bin/env node
// 从已冻结 JSON-RPC 原文提取独立标注草稿；不得把生产解析器输出当成真值。
// 生成后仍须人工复核每条事件、规则和 JSON Pointer。
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const historical = process.argv[3] === '--historical';
const scenarios = historical
  ? JSON.parse(readFileSync(resolve(root, 'eval/cases/historical-sources.json')))
  : JSON.parse(readFileSync(resolve(root, 'data/anvil-demo/transactions.json'))).scenarios;
const output = resolve(root, historical
  ? 'eval/cases/historical-ground-truth-draft.json'
  : 'eval/cases/anvil-ground-truth-draft.json');
const transferTopic = '0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef';
const approvalTopic = '0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925';
const signatures = new Map([
  ['0xa9059cbb', 'transfer(address,uint256)'],
  ['0x23b872dd', 'transferFrom(address,address,uint256)'],
  ['0x095ea7b3', 'approve(address,uint256)'],
  ['0x70a08231', 'balanceOf(address)'],
  ['0x18160ddd', 'totalSupply()'],
  ['0x42966c68', 'burn(uint256)'],
  ['0x40c10f19', 'mint(address,uint256)'],
  ['0xdd62ed3e', 'allowance(address,address)'],
  ['0x313ce567', 'decimals()'],
  ['0x95d89b41', 'symbol()'],
  ['0x06fdde03', 'name()'],
  ['0x42842e0e', 'safeTransferFrom(address,address,uint256)'],
  ['0xb88d4fde', 'safeTransferFrom(address,address,uint256,bytes)'],
  ['0x39509351', 'increaseAllowance(address,uint256)'],
  ['0xa457c2cd', 'decreaseAllowance(address,uint256)'],
  ['0xf2fde38b', 'transferOwnership(address)'],
]);
const hex = value => BigInt(value);
const address = topic => `0x${topic.slice(-40)}`.toLowerCase();
const expectedValue = (value, ref) => ({ applicable: true, value: String(value), rawJsonRef: ref });
function fixture(id, file) {
  return JSON.parse(readFileSync(resolve(root, 'eval/fixtures', id, `${file}.json`))).result;
}
function finding(ruleId, severity, confidence, rpcMethod, jsonPointer, logIndex = null) {
  return { ruleId, severity, confidence, rpcMethod, jsonPointer, logIndex };
}

const cases = scenarios.map(scenario => {
  const id = historical ? scenario.caseId : `anvil-${scenario.id}`;
  const tx = fixture(id, 'transaction');
  const receipt = fixture(id, 'receipt');
  const code = tx.to ? fixture(id, 'code') : null;
  const success = receipt.status === '0x1';
  const valueWei = hex(tx.value);
  const price = tx.type === '0x2' ? hex(receipt.effectiveGasPrice) : hex(tx.gasPrice);
  const gasCostWei = hex(receipt.gasUsed) * price;
  const toNetWei = success ? valueWei : 0n;
  const input = tx.input.toLowerCase();
  const selector = input.length >= 10 ? input.slice(0, 10) : null;
  const ambiguous = selector === '0x42966c68';
  const signature = ambiguous ? null : signatures.get(selector) ?? null;
  const transfers = [];
  const approvals = [];
  const findings = [];
  receipt.logs.forEach((log, index) => {
    if (log.topics.length !== 3 || !/^0x[0-9a-fA-F]{64}$/.test(log.data)) return;
    const common = {
      tokenAddress: log.address.toLowerCase(),
      valueWei: hex(log.data).toString(),
      logIndex: hex(log.logIndex).toString(),
      rawJsonRef: `/result/logs/${index}`,
    };
    if (log.topics[0].toLowerCase() === transferTopic) {
      const event = { ...common, from: address(log.topics[1]), to: address(log.topics[2]) };
      transfers.push(event);
      if (event.to === '0x0000000000000000000000000000000000000000') {
        findings.push(finding('TRANSFER-TO-ZERO-ADDRESS', 'MEDIUM', 1.0,
          'eth_getTransactionReceipt', common.rawJsonRef, common.logIndex));
      }
    } else if (log.topics[0].toLowerCase() === approvalTopic) {
      const event = { ...common, owner: address(log.topics[1]), spender: address(log.topics[2]) };
      approvals.push(event);
      if (hex(log.data) === (1n << 256n) - 1n) {
        findings.push(finding('ERC20-UNLIMITED-APPROVAL', 'HIGH', 1.0,
          'eth_getTransactionReceipt', common.rawJsonRef, common.logIndex));
      }
    }
  });
  if (!success) findings.push(finding('TX-FAILED', 'MEDIUM', 1.0,
    'eth_getTransactionReceipt', '/result/status'));
  if (input !== '0x' && !signature) findings.push(finding('UNKNOWN-SELECTOR', 'LOW', 0.8,
    'eth_getTransactionByHash', '/result/input'));
  if (tx.to && code === '0x' && input !== '0x') findings.push(finding('INTERACTION-WITH-EOA', 'LOW', 0.95,
    'eth_getCode', '/result'));
  if (transfers.length > 100) findings.push(finding('MANY-TOKEN-TRANSFERS', 'HIGH', 0.9,
    'eth_getTransactionReceipt', '/result/logs'));
  else if (transfers.length > 20) findings.push(finding('MANY-TOKEN-TRANSFERS', 'MEDIUM', 0.8,
    'eth_getTransactionReceipt', '/result/logs'));
  if (id === 'anvil-risk-address-hit') findings.push(finding('RISK-ADDRESS-HIT', 'HIGH', 0.85,
    'eth_getTransactionByHash', '/result/to'));

  if (!historical) {
    const expected = [...scenario.expectedRuleIds].sort();
    const actual = findings.map(f => f.ruleId).sort();
    if (JSON.stringify(expected) !== JSON.stringify(actual)) {
      throw new Error(`${id} 风险清单与独立标注不一致：${expected} / ${actual}`);
    }
  }
  return {
    caseId: id, sourceType: historical ? 'HISTORICAL' : 'ANVIL',
    network: historical ? 'mainnet' : 'anvil-local', chainId: historical ? 1 : 31337,
    txHash: scenario.txHash,
    source: historical
      ? { label: `Public Ethereum JSON-RPC snapshot (publicnode / 1rpc / Blast pool); ${scenario.category}`,
        uri: `https://etherscan.io/tx/${scenario.txHash}`,
        capturedAt: scenario.capturedAt ?? '2026-10-18',
        license: 'Self-authored annotations Apache-2.0; chain data public' }
      : { label: 'ChainLens Anvil deterministic seed', uri: `anvil://seed/${scenario.id}`,
        capturedAt: '2026-10-18', license: 'Apache-2.0' },
    fixtureDir: id,
    groundTruth: {
      status: expectedValue(success ? 'SUCCESS' : 'FAILED', '/result/status'),
      valueWei: expectedValue(valueWei, '/result/value'),
      gasCostWei: expectedValue(gasCostWei, '/result/gasUsed'),
      fromNetWei: expectedValue(-(gasCostWei + toNetWei), '/result/gasUsed'),
      toNetWei: expectedValue(toNetWei, '/result/value'),
      calldata: { applicable: true, status: ambiguous ? 'AMBIGUOUS' : signature ? 'RESOLVED' : 'UNRESOLVED',
        selector, resolvedSignature: signature, rawJsonRef: '/result/input' },
      transfers, approvals, expectedFindings: findings,
    },
  };
});
writeFileSync(output, `${JSON.stringify({ suiteId: 'transaction-eval', version: 'v1', cases }, null, 2)}\n`);
console.log(`已生成 ${cases.length} 条${historical ? '历史' : 'Anvil'}标注草稿：${output}`);

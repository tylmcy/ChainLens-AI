#!/usr/bin/env node
// 仅向显式临时路径写入故障注入副本；绝不修改仓库正式 ground truth。
import { existsSync, readFileSync, writeFileSync } from 'node:fs';

const [kind, target] = process.argv.slice(2);
if (!['parsing', 'rule'].includes(kind) || !target || existsSync(target)) {
  throw new Error('用法：node inject-offline-regression.mjs parsing|rule 不存在的临时目标.json');
}
const dataset = JSON.parse(readFileSync('eval/cases/transaction-eval-v1.json'));
if (kind === 'parsing') {
  for (const item of dataset.cases) {
    if (item.groundTruth.status.value === 'SUCCESS') item.groundTruth.status.value = 'FAILED';
  }
} else {
  const item = dataset.cases.find(value => value.caseId === 'anvil-plain-eth');
  if (!item) throw new Error('缺少 anvil-plain-eth');
  item.groundTruth.expectedFindings.push({
    ruleId: 'TX-FAILED', severity: 'MEDIUM', confidence: 1,
    rpcMethod: 'eth_getTransactionReceipt', jsonPointer: '/result/status', logIndex: null,
  });
}
writeFileSync(target, `${JSON.stringify(dataset, null, 2)}\n`);
console.log(`已生成 ${kind} 故障副本：${target}`);

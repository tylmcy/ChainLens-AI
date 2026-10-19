#!/usr/bin/env node
// 合并两份逐条复核后的 ground truth；只做机械合并，不修改链上事实或预期值。
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const load = name => JSON.parse(readFileSync(resolve(root, 'eval/cases', name)));
const anvil = load('anvil-ground-truth-draft.json');
const history = load('historical-ground-truth-draft.json');
if (anvil.cases.length !== 15 || history.cases.length !== 15) {
  throw new Error('Anvil 与历史用例必须各有 15 条');
}
const cases = [...anvil.cases, ...history.cases].sort((a, b) => a.caseId.localeCompare(b.caseId));
if (new Set(cases.map(item => item.caseId)).size !== 30) {
  throw new Error('caseId 存在重复');
}
const path = resolve(root, 'eval/cases/transaction-eval-v1.json');
writeFileSync(path, `${JSON.stringify({ suiteId: 'transaction-eval', version: 'v1', cases }, null, 2)}\n`);
console.log(`已合并 ${cases.length} 条用例：${path}`);

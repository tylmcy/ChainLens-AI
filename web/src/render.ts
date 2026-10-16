import type { AnalysisResult, Evidence, Finding } from './types';
import type { SseEvent } from './sse';

function element<K extends keyof HTMLElementTagNameMap>(tag: K, className?: string, content?: string): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (content !== undefined) node.textContent = content;
  return node;
}

function badge(text: string, className = ''): HTMLElement {
  return element('span', `badge ${className}`.trim(), text);
}

/** 事件只用于时间线展示；未知事件保留事件名，不信任 data 里的 HTML。 */
export function renderTimelineEvent(target: HTMLOListElement, event: SseEvent): void {
  const data = typeof event.data === 'object' && event.data !== null ? event.data as Record<string, unknown> : {};
  const labels: Record<string, string> = {
    'stage.started': '分析阶段', 'tool.started': '工具开始', 'tool.completed': '工具完成',
    'rules.completed': '规则检查', 'report.completed': '报告生成',
    'analysis.completed': '分析完成', 'analysis.failed': '分析失败',
  };
  const item = element('li', 'timeline-item');
  item.append(element('span', 'timeline-type', event.event), element('strong', '', labels[event.event] ?? '分析事件'));
  const detail = event.event === 'rules.completed' ? `命中 ${String(data.count ?? 0)} 条规则：${Array.isArray(data.ruleIds) ? data.ruleIds.join('、') : '无'}`
    : event.event === 'tool.started' || event.event === 'tool.completed' ? `${String(data.tool ?? '工具')} ${String(data.summary ?? '')}`
      : String(data.message ?? data.summary ?? '');
  if (detail) item.append(element('p', '', detail));
  target.append(item);
}

function addEvidenceRow(list: HTMLDListElement, label: string, value: unknown): void {
  if (value === null || value === undefined || value === '') return;
  list.append(element('dt', '', label), element('dd', '', String(value)));
}

/** 只从后端附带的原始 JSON 定位取值，不重新解释链上事实。 */
function rawAtPointer(result: AnalysisResult, evidence: Evidence): string | null {
  const method = evidence.rawJsonRef?.rpcMethod;
  const raw = method === 'eth_getTransactionReceipt' ? result.facts.receipt.rawJson
    : method === 'eth_getTransactionByHash' ? result.facts.transaction.rawJson
      : null;
  if (!raw) return null;
  try {
    // 数据库 rpc_payloads 以 {result: 原始 RPC 结果} 存储；Evidence 的 Pointer 指向这层包装。
    let current: unknown = { result: JSON.parse(raw) };
    const pointer = evidence.rawJsonRef.jsonPointer;
    if (!pointer.startsWith('/')) return null;
    for (const key of pointer.slice(1).split('/').map((part) => part.replace(/~1/g, '/').replace(/~0/g, '~'))) {
      if (typeof current !== 'object' || current === null || !(key in current)) return null;
      current = (current as Record<string, unknown>)[key];
    }
    return typeof current === 'string' ? current : JSON.stringify(current);
  } catch {
    return null;
  }
}

function renderFinding(result: AnalysisResult, finding: Finding): HTMLElement {
  const card = element('article', 'finding');
  const severity = finding.severity.toLowerCase();
  const heading = element('div', 'badges');
  heading.append(badge(finding.severity, severity), badge(finding.ruleId), badge(`置信度 ${Math.round(finding.confidence * 100)}%`));
  card.append(heading, element('h4', '', finding.title), element('p', '', finding.description));
  const details = element('details');
  details.append(element('summary', '', '查看链上证据与原始定位'));
  const fields = element('dl', 'evidence-grid');
  const evidence = finding.evidence;
  addEvidenceRow(fields, '交易哈希', evidence.transactionHash);
  addEvidenceRow(fields, '网络 / chainId', `${evidence.network} / ${evidence.chainId}`);
  addEvidenceRow(fields, '区块号', evidence.blockNumber);
  addEvidenceRow(fields, '日志索引', evidence.logIndex);
  addEvidenceRow(fields, 'RPC 方法', evidence.rpcMethod);
  addEvidenceRow(fields, 'JSON Pointer', evidence.rawJsonRef?.jsonPointer);
  addEvidenceRow(fields, '取证时间', evidence.fetchedAt);
  addEvidenceRow(fields, '原始值', rawAtPointer(result, evidence));
  details.append(fields);
  card.append(details);
  return card;
}

function reportBlock(title: string, items: string[]): HTMLElement {
  const block = element('section', 'report-block');
  block.append(element('h3', '', title));
  const list = element('ul');
  for (const item of items) list.append(element('li', '', item));
  block.append(list);
  return block;
}

/** 用 textContent 和 createElement 构造整份报告，API/LLM 文本不会进入 HTML 解析器。 */
export function renderReport(target: HTMLElement, result: AnalysisResult): void {
  target.replaceChildren();
  const card = element('article', 'report-card');
  const headline = element('div', 'report-topline');
  headline.append(badge(result.report.status === 'SUCCESS' ? '交易成功' : '交易失败', result.report.status === 'FAILED' ? 'high' : ''), badge(result.network), element('span', 'report-hash', result.txHash));
  card.append(headline, element('p', 'report-summary', result.report.summary));
  const flags = element('div', 'badges');
  flags.append(badge(result.llmUsed ? 'AI 报告' : '未使用 LLM'), badge(result.persisted ? '已保存历史' : '未持久化'));
  if (result.degraded) flags.append(badge('确定性降级报告', 'degraded'));
  card.append(flags);

  const grid = element('div', 'report-grid');
  grid.append(reportBlock('交易动作', result.report.actions.length ? result.report.actions : ['未解析出明确动作']));
  grid.append(reportBlock('来源', result.report.sources.length ? result.report.sources.map((source) => `${source.label} · ${source.reference}`) : ['当前无额外来源']));
  card.append(grid);

  const findings = element('section', 'report-block');
  findings.style.marginTop = '28px';
  findings.append(element('h3', '', `风险发现 · ${result.report.findings.length}`));
  if (!result.report.findings.length) findings.append(element('p', 'empty-note', '未命中当前规则。这不代表交易绝对安全。'));
  for (const finding of result.report.findings) findings.append(renderFinding(result, finding));
  card.append(findings);
  card.append(reportBlock('局限与提醒', result.report.limitations));
  target.append(card);
}

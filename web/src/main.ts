import './styles.css';
import { analyzeStream, extractTxHash, fetchAnalysis } from './api';
import { readHistory, removeHistory, saveHistory, type HistoryEntry } from './history';
import { renderReport, renderTimelineEvent } from './render';
import type { SseEvent } from './sse';
import type { AnalysisResult, ErrorResponse } from './types';

type PageState = 'idle' | 'running' | 'success' | 'error';

interface Dependencies {
  analyze: typeof analyzeStream;
  fetchById: typeof fetchAnalysis;
  storage: Storage;
}

function required<T extends HTMLElement>(root: Document, selector: string): T {
  const node = root.querySelector<T>(selector);
  if (!node) throw new Error(`页面缺少 ${selector}`);
  return node;
}

/** 将页面状态集中更新，避免异常路径遗留加载状态或禁用控件。 */
export function createApp(root: Document = document, deps: Dependencies = {
  analyze: analyzeStream, fetchById: fetchAnalysis, storage: localStorage,
}): { state: () => PageState } {
  const form = required<HTMLFormElement>(root, '#analysis-form');
  const input = required<HTMLInputElement>(root, '#tx-input');
  const submit = required<HTMLButtonElement>(root, '#submit-btn');
  const inputError = required<HTMLElement>(root, '#input-error');
  const status = required<HTMLElement>(root, '#status-message');
  const timelineSection = required<HTMLElement>(root, '#timeline-section');
  const timeline = required<HTMLOListElement>(root, '#timeline');
  const reportSection = required<HTMLElement>(root, '#report-section');
  const report = required<HTMLElement>(root, '#report');
  const historyList = required<HTMLUListElement>(root, '#history-list');
  let pageState: PageState = 'idle';
  let requestId = 0;
  let controller: AbortController | null = null;
  let activeInput = '';

  function setState(next: PageState, message: string): void {
    pageState = next;
    status.textContent = message;
    submit.disabled = next === 'running' && input.value.trim() === activeInput;
    const label = submit.querySelector('span');
    if (label) label.textContent = next === 'running' ? '分析中…' : '开始分析';
  }

  function showResult(result: AnalysisResult): void {
    renderReport(report, result);
    reportSection.classList.remove('hidden');
  }

  function showInputError(message: string): void {
    inputError.textContent = message;
    inputError.classList.remove('hidden');
    input.setAttribute('aria-invalid', 'true');
    input.focus();
  }

  function clearInputError(): void {
    inputError.textContent = '';
    inputError.classList.add('hidden');
    input.removeAttribute('aria-invalid');
  }

  function history(): HistoryEntry[] {
    try { return readHistory(deps.storage); } catch { return []; }
  }

  /** 仅显示本地索引；点击条目时从服务端读取完整历史，绝不重跑分析。 */
  function paintHistory(): void {
    historyList.replaceChildren();
    const entries = history();
    if (!entries.length) {
      const empty = root.createElement('li');
      empty.className = 'empty-note';
      empty.textContent = '还没有最近分析。完成一笔交易分析后会显示在这里。';
      historyList.append(empty);
      return;
    }
    for (const entry of entries) {
      const item = root.createElement('li');
      item.className = 'history-item';
      const open = root.createElement('button');
      open.className = 'history-open';
      open.type = 'button';
      open.setAttribute('aria-label', `打开 ${entry.txHash} 的历史分析`);
      const hash = root.createElement('strong');
      hash.textContent = entry.txHash;
      const date = root.createElement('small');
      date.textContent = entry.createdAt;
      open.append(hash, date);
      open.addEventListener('click', () => void openHistory(entry));
      const remove = root.createElement('button');
      remove.className = 'history-remove';
      remove.type = 'button';
      remove.textContent = '移除';
      remove.setAttribute('aria-label', `移除 ${entry.txHash} 的本地索引`);
      remove.addEventListener('click', () => {
        try { removeHistory(entry.analysisId, deps.storage); } catch { /* 浏览器禁用存储时只保留当前页 */ }
        paintHistory();
      });
      item.append(open, remove);
      historyList.append(item);
    }
  }

  async function openHistory(entry: HistoryEntry): Promise<void> {
    controller?.abort();
    const mine = ++requestId;
    controller = new AbortController();
    activeInput = '';
    clearInputError();
    setState('running', '正在读取已保存的分析…');
    try {
      const result = await deps.fetchById(entry.analysisId, controller.signal);
      if (mine !== requestId) return;
      timeline.replaceChildren();
      timelineSection.classList.add('hidden');
      showResult(result);
      setState('success', '已恢复历史报告；没有重新分析链上交易。');
    } catch (error) {
      if (mine !== requestId || (error instanceof Error && error.name === 'AbortError')) return;
      setState('error', `历史记录不可用：${error instanceof Error ? error.message : '读取失败'}。可移除本地索引。`);
    }
  }

  /** 每次新分析都拥有独立 token；被取消的旧流即使迟到也不能改写页面。 */
  async function startAnalysis(question: string): Promise<void> {
    controller?.abort();
    const mine = ++requestId;
    controller = new AbortController();
    activeInput = question;
    timeline.replaceChildren();
    timelineSection.classList.remove('hidden');
    report.replaceChildren();
    reportSection.classList.add('hidden');
    clearInputError();
    setState('running', '正在查询链上数据并执行规则…');
    const outcome: { completed: AnalysisResult | null } = { completed: null };
    try {
      await deps.analyze(question, (event: SseEvent) => {
        if (mine !== requestId) return;
        renderTimelineEvent(timeline, event);
        if (event.event === 'analysis.failed') {
          const data = event.data as Partial<ErrorResponse> | null;
          throw new Error(data?.message || '分析失败，请稍后重试');
        }
        if (event.event === 'analysis.completed') {
          outcome.completed = event.data as AnalysisResult;
          const completed = outcome.completed;
          showResult(completed);
          setState('success', '分析完成。可展开每条风险的原始证据定位。');
          try {
            saveHistory({ analysisId: completed.analysisId, txHash: completed.txHash, createdAt: completed.createdAt }, deps.storage);
            paintHistory();
          } catch { /* 本地存储不可用时仍展示分析报告 */ }
        }
      }, controller.signal);
      if (mine !== requestId) return;
      const completed = outcome.completed;
      if (!completed) throw new Error('分析流结束，但没有收到最终报告');
      // 后端完成事件可能先于落库。流结束后再查询一次，尽量显示最终 persisted 状态。
      if (!completed.persisted) {
        try {
          const stored = await deps.fetchById(completed.analysisId, controller.signal);
          if (mine === requestId) showResult(stored);
        } catch { /* 落库不可用时保留确定性报告和未持久化标记 */ }
      }
    } catch (error) {
      if (mine !== requestId || (error instanceof Error && error.name === 'AbortError')) return;
      report.replaceChildren();
      reportSection.classList.add('hidden');
      setState('error', `分析失败：${error instanceof Error ? error.message : '未知错误'}。可检查输入后重试。`);
    }
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const question = input.value.trim();
    if (!question) { showInputError('请输入交易哈希或包含哈希的问题。'); return; }
    if (!extractTxHash(question)) { showInputError('请提供且只提供一个完整交易哈希（0x + 64 位十六进制字符）。'); return; }
    if (pageState === 'running' && question === activeInput) return;
    void startAnalysis(question);
  });
  input.addEventListener('input', () => {
    clearInputError();
    if (pageState === 'running') submit.disabled = input.value.trim() === activeInput;
  });
  paintHistory();
  return { state: () => pageState };
}

if (typeof document !== 'undefined' && document.querySelector('#analysis-form')) createApp();

import { beforeEach, expect, it, vi } from 'vitest';
import { analysisFixture } from './analysis-fixture';
import { createApp } from './main';
import type { SseEvent } from './sse';

function mount(): void {
  document.body.innerHTML = `
    <form id="analysis-form"><input id="tx-input"><button id="submit-btn"><span>开始分析</span></button></form>
    <p id="input-error" class="hidden"></p><p id="status-message"></p>
    <section id="timeline-section" class="hidden"><ol id="timeline"></ol></section>
    <section id="report-section" class="hidden"><div id="report"></div></section>
    <ul id="history-list"></ul>`;
}

function submit(value: string): void {
  const input = document.querySelector<HTMLInputElement>('#tx-input')!;
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  document.querySelector<HTMLFormElement>('#analysis-form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

beforeEach(() => { localStorage.clear(); mount(); });

it('空值与非法哈希不会发请求，完成事件显示报告并保存轻量历史', async () => {
  const analyze = vi.fn(async (_input: string, onEvent: (event: SseEvent) => void) => {
    onEvent({ event: 'stage.started', data: { stage: 'A', message: '正在取证' } });
    onEvent({ event: 'analysis.completed', data: analysisFixture });
  });
  const fetchById = vi.fn(async () => analysisFixture);
  const app = createApp(document, { analyze, fetchById, storage: localStorage });
  submit('');
  submit('0x123');
  expect(analyze).not.toHaveBeenCalled();
  submit(analysisFixture.txHash);
  await vi.waitFor(() => expect(app.state()).toBe('success'));
  expect(document.querySelector('#timeline')?.textContent).toContain('正在取证');
  expect(document.querySelector('#report')?.textContent).toContain('ERC20-UNLIMITED-APPROVAL');
  expect(localStorage.getItem('chainlens.recent-analyses.v1')).toContain('demo-analysis-1');
  expect(localStorage.getItem('chainlens.recent-analyses.v1')).not.toContain('rawJson');
});

it('失败事件恢复提交状态且不伪造报告', async () => {
  const analyze = vi.fn(async (_input: string, onEvent: (event: SseEvent) => void) => {
    onEvent({ event: 'analysis.failed', data: { message: 'RPC 不可用' } });
  });
  const app = createApp(document, { analyze, fetchById: vi.fn(), storage: localStorage });
  submit(analysisFixture.txHash);
  await vi.waitFor(() => expect(app.state()).toBe('error'));
  expect(document.querySelector('#status-message')?.textContent).toContain('RPC 不可用');
  expect(document.querySelector('#report-section')?.classList.contains('hidden')).toBe(true);
  expect(document.querySelector<HTMLButtonElement>('#submit-btn')?.disabled).toBe(false);
});

it('新请求取消旧流，旧结果迟到也不能覆盖新报告', async () => {
  const pending: Array<{ resolve: () => void; onEvent: (event: SseEvent) => void; signal?: AbortSignal }> = [];
  const analyze = vi.fn((_input: string, onEvent: (event: SseEvent) => void, signal?: AbortSignal) => new Promise<void>((resolve) => pending.push({ resolve, onEvent, signal })));
  const app = createApp(document, { analyze, fetchById: vi.fn(), storage: localStorage });
  submit(analysisFixture.txHash);
  submit(`0x${'b'.repeat(64)}`);
  expect(pending[0].signal?.aborted).toBe(true);
  pending[1].onEvent({ event: 'analysis.completed', data: { ...analysisFixture, report: { ...analysisFixture.report, summary: '新结果' }, persisted: true } });
  pending[1].resolve();
  await vi.waitFor(() => expect(app.state()).toBe('success'));
  pending[0].onEvent({ event: 'analysis.completed', data: { ...analysisFixture, report: { ...analysisFixture.report, summary: '旧结果' } } });
  pending[0].resolve();
  expect(document.querySelector('#report')?.textContent).toContain('新结果');
  expect(document.querySelector('#report')?.textContent).not.toContain('旧结果');
});

it('点击历史只调用 GET；失效时可以移除本地索引', async () => {
  localStorage.setItem('chainlens.recent-analyses.v1', JSON.stringify([{ analysisId: 'demo-analysis-1', txHash: analysisFixture.txHash, createdAt: analysisFixture.createdAt }]));
  const analyze = vi.fn();
  const fetchById = vi.fn().mockRejectedValue(new Error('HTTP 404'));
  const app = createApp(document, { analyze, fetchById, storage: localStorage });
  document.querySelector<HTMLButtonElement>('.history-open')!.click();
  await vi.waitFor(() => expect(app.state()).toBe('error'));
  expect(fetchById).toHaveBeenCalledWith('demo-analysis-1', expect.any(AbortSignal));
  expect(analyze).not.toHaveBeenCalled();
  document.querySelector<HTMLButtonElement>('.history-remove')!.click();
  expect(localStorage.getItem('chainlens.recent-analyses.v1')).toBe('[]');
});

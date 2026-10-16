import { expect, it } from 'vitest';
import { analysisFixture } from './analysis-fixture';
import { renderReport, renderTimelineEvent } from './render';

it('显示完整降级报告、规则依据与原始 RPC 值', () => {
  const target = document.createElement('div');
  renderReport(target, analysisFixture);
  expect(target.textContent).toContain('确定性降级报告');
  expect(target.textContent).toContain('ERC20-UNLIMITED-APPROVAL');
  expect(target.textContent).toContain('置信度 100%');
  expect(target.textContent).toContain('不构成投资建议');
  expect(target.textContent).toContain('授权代币支出');
  expect(target.textContent).toContain('/result/logs/0/data');
  expect(target.textContent).toContain('0xffff');
});

it('恶意报告与事件内容只作为文本显示', () => {
  const target = document.createElement('div');
  const malicious = { ...analysisFixture, report: { ...analysisFixture.report, summary: '<img src=x onerror=alert(1)>' } };
  renderReport(target, malicious);
  expect(target.textContent).toContain('<img src=x onerror=alert(1)>');
  expect(target.querySelector('img')).toBeNull();
  const timeline = document.createElement('ol');
  renderTimelineEvent(timeline, { event: 'stage.started', data: { message: '<script>alert(1)</script>' } });
  expect(timeline.querySelector('script')).toBeNull();
  expect(timeline.textContent).toContain('<script>alert(1)</script>');
});

it('无 finding 时写明规则覆盖有限', () => {
  const target = document.createElement('div');
  renderReport(target, { ...analysisFixture, report: { ...analysisFixture.report, findings: [] }, llmUsed: true, degraded: false, persisted: false });
  expect(target.textContent).toContain('未命中当前规则');
  expect(target.textContent).toContain('AI 报告');
  expect(target.textContent).toContain('未持久化');
});

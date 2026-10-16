import { beforeEach, expect, it } from 'vitest';
import { readHistory, removeHistory, saveHistory } from './history';

beforeEach(() => localStorage.clear());

it('损坏数据回退为空列表', () => {
  localStorage.setItem('chainlens.recent-analyses.v1', '{bad');
  expect(readHistory()).toEqual([]);
});

it('按 ID 去重、倒序并限制十条', () => {
  for (let index = 0; index < 12; index++) {
    saveHistory({ analysisId: `id-${index}`, txHash: `0x${index}`, createdAt: `t${index}` });
  }
  saveHistory({ analysisId: 'id-5', txHash: 'updated', createdAt: 'new' });
  expect(readHistory()).toHaveLength(10);
  expect(readHistory()[0]).toEqual({ analysisId: 'id-5', txHash: 'updated', createdAt: 'new' });
  expect(readHistory().filter((entry) => entry.analysisId === 'id-5')).toHaveLength(1);
  expect(removeHistory('id-5').some((entry) => entry.analysisId === 'id-5')).toBe(false);
});

export interface HistoryEntry {
  analysisId: string;
  txHash: string;
  createdAt: string;
}

const KEY = 'chainlens.recent-analyses.v1';
const LIMIT = 10;

/** 只读轻量索引；损坏或旧格式数据不会阻断页面启动。 */
export function readHistory(storage: Storage = localStorage): HistoryEntry[] {
  try {
    const parsed: unknown = JSON.parse(storage.getItem(KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is HistoryEntry =>
      typeof item === 'object' && item !== null &&
      typeof item.analysisId === 'string' &&
      typeof item.txHash === 'string' &&
      typeof item.createdAt === 'string').slice(0, LIMIT);
  } catch {
    return [];
  }
}

/** 新结果置顶、按 ID 去重；不保存 API Key、完整报告或 RPC 原文。 */
export function saveHistory(entry: HistoryEntry, storage: Storage = localStorage): HistoryEntry[] {
  const next = [entry, ...readHistory(storage).filter((item) => item.analysisId !== entry.analysisId)].slice(0, LIMIT);
  storage.setItem(KEY, JSON.stringify(next));
  return next;
}

/** 历史查询 404 或用户主动清理时移除单条本地索引。 */
export function removeHistory(analysisId: string, storage: Storage = localStorage): HistoryEntry[] {
  const next = readHistory(storage).filter((item) => item.analysisId !== analysisId);
  storage.setItem(KEY, JSON.stringify(next));
  return next;
}

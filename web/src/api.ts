import { readSseStream, type SseEvent } from './sse';
import type { AnalysisResult, ErrorResponse } from './types';

const TX_HASH_PATTERN = /0x[0-9a-fA-F]{64}(?![0-9a-fA-F])/g;

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

/** 纯正则提取哈希（与后端阶段 0 一致，设计文档 1.3）。 */
export function extractTxHash(question: string): string | null {
  const matches = [...question.matchAll(TX_HASH_PATTERN)];
  return matches.length === 1 ? matches[0][0] : null;
}

/** 同步分析（完整 JSON）。 */
export async function analyzeJson(input: string): Promise<AnalysisResult> {
  const response = await fetch('/api/v1/analyses', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: input }),
  });
  if (!response.ok) {
    throw await toError(response);
  }
  return (await response.json()) as AnalysisResult;
}

/** SSE 直播分析：每个事件回调一次。 */
export async function analyzeStream(
  input: string,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch('/api/v1/analyses', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ question: input }),
    signal,
  });
  if (!response.ok) {
    throw await toError(response);
  }
  await readSseStream(response, onEvent);
}

/** 历史分析列表（V1 无列表端点：由前端 localStorage 记录 analysisId，逐个查询）。 */
export async function fetchAnalysis(analysisId: string, signal?: AbortSignal): Promise<AnalysisResult> {
  const response = await fetch(`/api/v1/analyses/${encodeURIComponent(analysisId)}`, { signal });
  if (!response.ok) {
    throw await toError(response);
  }
  return (await response.json()) as AnalysisResult;
}

async function toError(response: Response): Promise<ApiError> {
  let message = `HTTP ${response.status}`;
  let code: string | undefined;
  try {
    const body = (await response.json()) as ErrorResponse;
    message = body.message || message;
    code = body.code;
  } catch {
    // 非 JSON 错误体，保留 HTTP 状态信息
  }
  return new ApiError(message, response.status, code);
}

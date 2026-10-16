/** POST SSE 读取器：EventSource 不支持 POST，因此使用 fetch 的 ReadableStream。 */
export interface SseEvent {
  event: string;
  data: unknown;
}

/** 保留未完成的行与事件，避免网络分块恰好切在 CRLF、JSON 或 UTF-8 字符中间时丢数据。 */
export class SseParser {
  private buffer = '';
  private eventName = '';
  private dataLines: string[] = [];

  constructor(private readonly onEvent: (event: SseEvent) => void) {}

  push(text: string, final = false): void {
    this.buffer += text;
    let index = 0;
    while (index < this.buffer.length) {
      const char = this.buffer[index];
      if (char !== '\n' && char !== '\r') {
        index++;
        continue;
      }
      // CRLF 可能横跨两个网络数据块；非流尾时留待下一块确认。
      if (char === '\r' && index === this.buffer.length - 1 && !final) break;
      const line = this.buffer.slice(0, index);
      const width = char === '\r' && this.buffer[index + 1] === '\n' ? 2 : 1;
      this.buffer = this.buffer.slice(index + width);
      index = 0;
      this.consumeLine(line);
    }
    if (final) {
      if (this.buffer) this.consumeLine(this.buffer);
      this.buffer = '';
      this.flush();
    }
  }

  private consumeLine(line: string): void {
    if (line === '') {
      this.flush();
      return;
    }
    if (line.startsWith(':')) return;
    const separator = line.indexOf(':');
    const field = separator < 0 ? line : line.slice(0, separator);
    const rawValue = separator < 0 ? '' : line.slice(separator + 1);
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;
    if (field === 'event') this.eventName = value;
    if (field === 'data') this.dataLines.push(value);
  }

  private flush(): void {
    if (this.dataLines.length) {
      const raw = this.dataLines.join('\n');
      let data: unknown = raw;
      try {
        data = JSON.parse(raw);
      } catch {
        // 后端目前发送 JSON；非 JSON 消息保留原文交给 UI 做可理解的错误提示。
      }
      this.onEvent({ event: this.eventName || 'message', data });
    }
    this.eventName = '';
    this.dataLines = [];
  }
}

/** 一次性解析完整 SSE 文本，主要供测试与诊断使用。 */
export function parseSseStream(text: string): SseEvent[] {
  const events: SseEvent[] = [];
  new SseParser((event) => events.push(event)).push(text, true);
  return events;
}

/** 逐块读取 POST 响应；TextDecoder 保留跨块 UTF-8 状态。 */
export async function readSseStream(response: Response, onEvent: (event: SseEvent) => void): Promise<void> {
  if (!response.body) throw new Error('响应不支持流式读取');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const parser = new SseParser(onEvent);
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      parser.push(decoder.decode(value, { stream: true }));
    }
    parser.push(decoder.decode(), true);
  } finally {
    reader.releaseLock();
  }
}

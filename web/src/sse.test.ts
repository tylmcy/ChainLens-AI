import { describe, expect, it } from 'vitest';
import { parseSseStream, readSseStream, SseParser, type SseEvent } from './sse';

describe('SSE 解析', () => {
  it('在 CRLF、LF 和任意分块下只分发完整事件', () => {
    const events: SseEvent[] = [];
    const parser = new SseParser((event) => events.push(event));
    for (const chunk of ['event: tool.started\r', '\ndata: {"tool":"get', 'Transaction"}\r', '\n\r', '\nevent: rules.completed\n', 'data: {"count":1,', '\ndata: "ruleIds":["R1"]}\n\n']) {
      parser.push(chunk);
    }
    parser.push('', true);
    expect(events).toEqual([
      { event: 'tool.started', data: { tool: 'getTransaction' } },
      { event: 'rules.completed', data: { count: 1, ruleIds: ['R1'] } },
    ]);
  });

  it('处理没有尾随空行的失败事件与注释', () => {
    expect(parseSseStream(': keepalive\nevent: analysis.failed\ndata: {"message":"RPC 不可用"}'))
      .toEqual([{ event: 'analysis.failed', data: { message: 'RPC 不可用' } }]);
  });

  it('保留跨字节块的 UTF-8 中文', async () => {
    const bytes = new TextEncoder().encode('event: stage.started\ndata: {"message":"解析中"}\n\n');
    const split = bytes.indexOf(0xe8) + 1;
    const response = new Response(new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(bytes.slice(0, split));
        controller.enqueue(bytes.slice(split));
        controller.close();
      },
    }));
    const events: SseEvent[] = [];
    await readSseStream(response, (event) => events.push(event));
    expect(events).toEqual([{ event: 'stage.started', data: { message: '解析中' } }]);
  });
});

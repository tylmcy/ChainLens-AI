package io.chainlens.model;

import java.time.Instant;

/**
 * 一次工具调用记录（时间线回放与工具调用成功率统计的数据源）。
 * M3 阶段恒为空列表，M5 Agent 编排后产生真实记录。
 */
public record ToolCallStep(
        String toolName,
        String arguments,
        Instant startedAt,
        long durationMs,
        boolean success,
        String summary
) {
}

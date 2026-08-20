package io.chainlens.model;

import java.time.Instant;
import java.util.List;

/**
 * 一次分析的完整产物：案卷 + 规则命中 + 报告 + 运行状态。
 *
 * @param llmUsed  是否使用了 LLM（降级路径为 false，设计文档 3.3）
 * @param degraded 是否发生了降级（如 LLM 不可用）
 * @param persisted 是否已持久化（DB 失败时 false，结果仍返回，设计文档 9.3）
 */
public record AnalysisResult(
        String analysisId,
        String txHash,
        Network network,
        AnalysisReport report,
        TxFacts facts,
        List<Finding> findings,
        List<ToolCallStep> toolSteps,
        boolean llmUsed,
        boolean degraded,
        boolean persisted,
        long totalDurationMs,
        Instant createdAt
) {
}

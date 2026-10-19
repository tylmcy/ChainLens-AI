package io.chainlens.evaluation;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.Instant;

import io.chainlens.evaluation.EvaluationResult.Difference;

/** Full 模式机器结果；全部指标保留原始计数，百分比由消费方计算。 */
public record FullEvaluationResult(
        String suiteId,
        String datasetVersion,
        String ruleVersion,
        String mode,
        String gitCommit,
        Instant executedAt,
        List<Case> cases,
        Count parsingAccuracy,
        Count ruleAccuracy,
        Count toolSuccessRate,
        Count factConsistencyRate,
        UsageSummary modelUsage,
        Map<String, Latency> latencyNanos,
        ModelBudget.Snapshot budget,
        List<String> unexecutedCaseIds,
        boolean passed
) {
    public record Count(long correct, long total) {
        public double ratio() {
            return total == 0 ? Double.NaN : (double) correct / total;
        }
    }

    /** 最近秩法：P50/P95 = 升序样本中 ceil(p×n) 的值；样本数为零时分位值为空。 */
    public record Latency(int samples, Long p50, Long p95) {
    }

    /** 每次模型响应的原始 usage；缺失字段保留 null，不用估算值填充。 */
    public record ModelCallUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    }

    /** 输入/输出汇总仅包含可结算的调用，provider 未报告的 total 不作推算。 */
    public record UsageSummary(long calls, long completeCalls, long incompleteCalls,
                               long inputTokens, long outputTokens, long reportedTotalTokens,
                               long missingReportedTotalCalls) {
    }

    public record Case(String caseId, String txHash, boolean passed, long successfulTools,
                       long totalTools, long supportedFacts, long checkedFacts,
                       BigDecimal verifiedCostUsd, List<ModelCallUsage> modelCalls,
                       Map<String, Long> segmentNanos,
                       List<Difference> differences) {
    }
}

package io.chainlens.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 机器可读评测结果；Markdown 摘要必须从同一对象渲染。 */
public record EvaluationResult(
        String suiteId,
        String datasetVersion,
        String ruleVersion,
        String mode,
        String gitCommit,
        Instant executedAt,
        Metric parsingAccuracy,
        RuleMetric ruleAccuracy,
        Map<String, Metric> parsingByCategory,
        List<CaseResult> cases,
        boolean passed
) {
    /** 分子与分母是计分事实，百分比只能从它们计算。 */
    public record Metric(long correct, long total, double threshold) {
        public double ratio() {
            return total == 0 ? Double.NaN : (double) correct / total;
        }
    }

    public record RuleMetric(long truePositive, long trueNegative, long falsePositive,
                             long falseNegative, double threshold) {
        public long correct() {
            return truePositive + trueNegative;
        }

        public long total() {
            return correct() + falsePositive + falseNegative;
        }

        public double ratio() {
            return total() == 0 ? Double.NaN : (double) correct() / total();
        }
    }

    public record CaseResult(String caseId, String txHash, boolean passed, List<Difference> differences) {
    }

    /** 分类可为 PARSING、TP、TN、FP、FN 或 STRUCTURE；证据位置不能为空白字符串。 */
    public record Difference(String caseId, String category, String fieldPath, String ruleId,
                             String expected, String actual, String confidence,
                             String evidence, String rawJsonRef) {
    }
}

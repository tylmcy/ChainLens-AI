package io.chainlens.evaluation;

import io.chainlens.evaluation.EvaluationResult.Metric;
import io.chainlens.evaluation.EvaluationResult.RuleMetric;

/** CI 门槛使用整数计数比较，避免 97.99% 经显示舍入后误判为 98%。 */
public final class EvaluationThresholds {

    private EvaluationThresholds() {
    }

    public static boolean offlinePassed(Metric parsing, RuleMetric rules) {
        return parsing.total() > 0 && parsing.correct() >= 0
                && parsing.correct() * 100 >= parsing.total() * 98
                && rules.total() == 210 && rules.falsePositive() == 0 && rules.falseNegative() == 0
                && rules.correct() == rules.total();
    }
}

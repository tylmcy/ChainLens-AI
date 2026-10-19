package io.chainlens.evaluation;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.chainlens.evaluation.EvaluationDataset.CalldataExpectation;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationDataset.ExpectedFinding;
import io.chainlens.evaluation.EvaluationDataset.ExpectedValue;
import io.chainlens.evaluation.EvaluationDataset.GroundTruth;
import io.chainlens.evaluation.EvaluationDataset.Source;
import io.chainlens.evaluation.EvaluationDataset.SourceType;
import io.chainlens.evaluation.EvaluationResult.Metric;
import io.chainlens.evaluation.EvaluationResult.RuleMetric;
import io.chainlens.model.Evidence;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;

import static org.assertj.core.api.Assertions.assertThat;

class RuleComparatorTest {

    private static final String RULE = "TX-FAILED";
    private static final List<String> IDS = EvaluationRuleSet.create().rules().stream()
            .map(rule -> rule.id()).toList();

    @Test
    void classifiesTruePositiveAndTrueNegative() {
        RuleComparator.Result result = RuleComparator.compare(item(true), List.of(finding(true)), IDS);
        assertThat(result.metric().truePositive()).isEqualTo(1);
        assertThat(result.metric().trueNegative()).isEqualTo(6);
        assertThat(result.metric().falsePositive()).isZero();
        assertThat(result.metric().falseNegative()).isZero();
        assertThat(result.differences()).isEmpty();
    }

    @Test
    void classifiesFalsePositiveAndFalseNegative() {
        RuleComparator.Result fp = RuleComparator.compare(item(false), List.of(finding(true)), IDS);
        RuleComparator.Result fn = RuleComparator.compare(item(true), List.of(), IDS);
        assertThat(fp.metric().falsePositive()).isEqualTo(1);
        assertThat(fp.differences()).singleElement().satisfies(d -> {
            assertThat(d.category()).isEqualTo("FP");
            assertThat(d.ruleId()).isEqualTo(RULE);
        });
        assertThat(fn.metric().falseNegative()).isEqualTo(1);
        assertThat(fn.differences()).singleElement().satisfies(d -> {
            assertThat(d.category()).isEqualTo("FN");
            assertThat(d.rawJsonRef()).isEqualTo("/result/status");
        });
    }

    @Test
    void malformedEvidenceMakesExpectedHitFail() {
        RuleComparator.Result result = RuleComparator.compare(item(true), List.of(finding(false)), IDS);
        assertThat(result.metric().falseNegative()).isEqualTo(1);
        assertThat(result.differences()).singleElement().satisfies(d -> {
            assertThat(d.category()).isEqualTo("STRUCTURE");
            assertThat(d.evidence()).contains("原始定位不一致");
        });
    }

    @Test
    void integerThresholdDoesNotRoundUp() {
        assertThat(EvaluationThresholds.offlinePassed(new Metric(49, 50, .98),
                new RuleMetric(15, 195, 0, 0, 1))).isTrue();
        assertThat(EvaluationThresholds.offlinePassed(new Metric(9799, 10000, .98),
                new RuleMetric(15, 195, 0, 0, 1))).isFalse();
        assertThat(EvaluationThresholds.offlinePassed(new Metric(50, 50, .98),
                new RuleMetric(15, 194, 1, 0, 1))).isFalse();
    }

    private static EvaluationCase item(boolean expectsRule) {
        ExpectedValue status = new ExpectedValue(true, "FAILED", "/result/status");
        ExpectedValue zero = new ExpectedValue(true, "0", "/result/value");
        List<ExpectedFinding> findings = expectsRule
                ? List.of(new ExpectedFinding(RULE, "HIGH", 1.0,
                "eth_getTransactionReceipt", "/result/status", null)) : List.of();
        GroundTruth truth = new GroundTruth(status, zero, zero, zero, zero,
                new CalldataExpectation(true, "UNRESOLVED", null, null, "/result/input"),
                List.of(), List.of(), findings);
        return new EvaluationCase("sample", SourceType.ANVIL, "anvil-local", 31337,
                EvaluationDatasetLoaderTest.HASH,
                new Source("test", "anvil://test", "2026-10-18", "Apache-2.0"), "sample", truth);
    }

    private static Finding finding(boolean validEvidence) {
        Evidence evidence = new Evidence(BigInteger.valueOf(31337), "anvil-local", BigInteger.ONE,
                EvaluationDatasetLoaderTest.HASH, null, "eth_getTransactionReceipt", Instant.EPOCH,
                new Evidence.RawJsonRef("eth_getTransactionReceipt",
                        validEvidence ? "/result/status" : "/result/other"));
        return new Finding(RULE, Severity.HIGH, 1.0, "失败", "失败", evidence);
    }
}

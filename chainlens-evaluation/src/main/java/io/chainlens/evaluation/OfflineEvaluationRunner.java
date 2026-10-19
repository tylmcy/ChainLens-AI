package io.chainlens.evaluation;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.chainlens.chain.TxFactsAssembler;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationResult.CaseResult;
import io.chainlens.evaluation.EvaluationResult.Difference;
import io.chainlens.evaluation.EvaluationResult.Metric;
import io.chainlens.evaluation.EvaluationResult.RuleMetric;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;

/** 完全本地执行交易解析与七条规则；只负责回放、比较、聚合。 */
public final class OfflineEvaluationRunner {

    public static final String RULE_VERSION = "7-rules-defaults-v1";

    private final RuleEngine rules;

    public OfflineEvaluationRunner() {
        this(EvaluationRuleSet.create());
    }

    public OfflineEvaluationRunner(RuleEngine rules) {
        this.rules = rules;
    }

    /** 对已经整体预检的数据集逐例执行；单例解析错误记录为失败，不让它从质量门槛中消失。 */
    public EvaluationResult run(EvaluationDataset dataset, Path fixtureRoot, String gitCommit) {
        long parsedCorrect = 0, parsedTotal = 0;
        long tp = 0, tn = 0, fp = 0, fn = 0;
        boolean executionError = false;
        Map<String, ParsingComparator.Count> categories = new LinkedHashMap<>();
        List<CaseResult> cases = new ArrayList<>();
        List<String> ruleIds = rules.rules().stream().map(rule -> rule.id()).toList();

        for (EvaluationCase item : dataset.cases().stream()
                .sorted(Comparator.comparing(EvaluationCase::caseId)).toList()) {
            List<Difference> differences = new ArrayList<>();
            try {
                FixtureEthRpcClient rpc = new FixtureEthRpcClient(item.caseId(), item.txHash(),
                        fixtureRoot.resolve(item.fixtureDir()));
                TxFacts facts = new TxFactsAssembler(rpc).assemble(item.txHash());
                List<Finding> findings = rules.evaluate(facts);
                ParsingComparator.Result parsing = ParsingComparator.compare(item, facts);
                RuleComparator.Result risk = RuleComparator.compare(item, findings, ruleIds);
                parsedCorrect += parsing.overall().correct();
                parsedTotal += parsing.overall().total();
                parsing.categories().forEach((name, count) -> categories.merge(name, count,
                        ParsingComparator.Count::plus));
                tp += risk.metric().truePositive();
                tn += risk.metric().trueNegative();
                fp += risk.metric().falsePositive();
                fn += risk.metric().falseNegative();
                differences.addAll(parsing.differences());
                differences.addAll(risk.differences());
            } catch (RuntimeException e) {
                executionError = true;
                differences.add(new Difference(item.caseId(), "ERROR", "execution", null,
                        "完整取证、解析和七规则结果", e.getClass().getSimpleName(),
                        null, e.getMessage(), "/result"));
            }
            differences.sort(Comparator.comparing(Difference::fieldPath)
                    .thenComparing(d -> d.ruleId() == null ? "" : d.ruleId()));
            cases.add(new CaseResult(item.caseId(), item.txHash(), differences.isEmpty(), List.copyOf(differences)));
        }

        Metric parsing = new Metric(parsedCorrect, parsedTotal, .98);
        RuleMetric risk = new RuleMetric(tp, tn, fp, fn, 1.0);
        Map<String, Metric> byCategory = new LinkedHashMap<>();
        categories.forEach((name, count) -> byCategory.put(name,
                new Metric(count.correct(), count.total(), .98)));
        boolean passed = !executionError && EvaluationThresholds.offlinePassed(parsing, risk);
        return new EvaluationResult(dataset.suiteId(), dataset.version(), RULE_VERSION,
                "offline", gitCommit, Instant.now(), parsing, risk,
                Map.copyOf(byCategory), List.copyOf(cases), passed);
    }
}

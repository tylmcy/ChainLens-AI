package io.chainlens.evaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.time.Instant;

import io.chainlens.agent.AnalysisProgressListener;
import io.chainlens.agent.FactConsistencyValidator;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationResult.Difference;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.ToolCallStep;

/** 批量调用真实 AnalysisService，并复用 offline 比较器计算 Full 模式的机械指标。 */
public final class FullEvaluationRunner {

    private static final List<String> SEGMENTS = List.of("endToEnd", "rpc", "parsing", "rules", "llm");
    private static final double TOOL_THRESHOLD = .95;
    private static final double FACT_THRESHOLD = .98;

    private final FullNetworkRouter router;

    public FullEvaluationRunner(FullNetworkRouter router) {
        this.router = router;
    }

    /** 显式 caseId 子集用于低成本冒烟；空集合代表完整 30 条。 */
    public FullEvaluationResult run(EvaluationDataset dataset, Set<String> selectedIds,
                                    ModelBudget.Config budgetConfig) {
        return run(dataset, selectedIds, budgetConfig, GitRevision.current(Path.of(".")));
    }

    /** CI 可注入确切 commit；本地无法取得时调用方明确传 unavailable。 */
    public FullEvaluationResult run(EvaluationDataset dataset, Set<String> selectedIds,
                                    ModelBudget.Config budgetConfig, String gitCommit) {
        List<EvaluationCase> selected = dataset.cases().stream()
                .filter(item -> selectedIds.isEmpty() || selectedIds.contains(item.caseId()))
                .sorted(Comparator.comparing(EvaluationCase::caseId)).toList();
        if (selected.isEmpty() || (!selectedIds.isEmpty() && selected.size() != selectedIds.size())) {
            throw new IllegalArgumentException("Full 评测子集为空或包含未知 caseId: " + selectedIds);
        }
        ModelBudget budget = new ModelBudget(budgetConfig);
        router.preflight(selected);

        long parsingCorrect = 0, parsingTotal = 0, ruleCorrect = 0, ruleTotal = 0;
        long toolsSuccess = 0, toolsTotal = 0, factsSupported = 0, factsChecked = 0;
        boolean anomaly = false;
        Map<String, List<Long>> durationSamples = new LinkedHashMap<>();
        SEGMENTS.forEach(segment -> durationSamples.put(segment, new ArrayList<>()));
        List<FullEvaluationResult.Case> cases = new ArrayList<>();
        List<String> ruleIds = EvaluationRuleSet.create().rules().stream().map(rule -> rule.id()).toList();

        for (EvaluationCase item : selected) {
            if (budget.stopped()) {
                break;
            }
            budget.beginCase(item.caseId());
            Collector collector = new Collector(budget);
            List<Difference> differences = new ArrayList<>();
            long started = System.nanoTime();
            long caseToolsSuccess = 0, caseToolsTotal = 0, caseFactsSupported = 0, caseFactsChecked = 0;
            try {
                AnalysisResult result = router.analyze(item, collector);
                if (!result.llmUsed() || result.degraded()) {
                    differences.add(error(item, "LLM 未真正完成或报告已降级"));
                }
                ParsingComparator.Result parsing = ParsingComparator.compare(item, result.facts());
                RuleComparator.Result rules = RuleComparator.compare(item, result.findings(), ruleIds);
                parsingCorrect += parsing.overall().correct();
                parsingTotal += parsing.overall().total();
                ruleCorrect += rules.metric().correct();
                ruleTotal += rules.metric().total();
                differences.addAll(parsing.differences());
                differences.addAll(rules.differences());

                for (ToolCallStep step : result.toolSteps()) {
                    caseToolsTotal++;
                    if (step.success()) {
                        caseToolsSuccess++;
                    }
                }
                if (caseToolsTotal == 0) {
                    differences.add(error(item, "没有可核验的工具调用步骤"));
                }

                FactConsistencyValidator.Score factScore = FactConsistencyValidator.score(
                        result.report(), result.facts(), result.findings());
                caseFactsChecked = factScore.checkedTokens();
                caseFactsSupported = factScore.passedTokens();
                if (caseFactsChecked == 0) {
                    differences.add(error(item, "事实 token 数为零，不能计算事实一致率"));
                }
                for (String violation : factScore.violations()) {
                    differences.add(error(item, "事实护栏：" + violation));
                }
                if (collector.modelCalls == 0 || collector.missingUsage) {
                    differences.add(error(item, "模型调用或 token usage 缺失"));
                }
            } catch (RuntimeException e) {
                // RPC/模型异常原文可能带 URL 查询参数或凭据；公开产物仅保留异常类型。
                differences.add(error(item, e.getClass().getSimpleName()));
            } finally {
                collector.addTiming("endToEnd", System.nanoTime() - started);
            }
            toolsSuccess += caseToolsSuccess;
            toolsTotal += caseToolsTotal;
            factsSupported += caseFactsSupported;
            factsChecked += caseFactsChecked;
            collector.durations.forEach((segment, nanos) -> durationSamples.get(segment).add(nanos));
            differences.sort(Comparator.comparing(Difference::fieldPath)
                    .thenComparing(d -> d.ruleId() == null ? "" : d.ruleId()));
            anomaly |= !differences.isEmpty();
            cases.add(new FullEvaluationResult.Case(item.caseId(), item.txHash(), differences.isEmpty(),
                    caseToolsSuccess, caseToolsTotal, caseFactsSupported, caseFactsChecked,
                    budget.snapshot().caseCostsUsd().getOrDefault(item.caseId(), java.math.BigDecimal.ZERO),
                    List.copyOf(collector.usages), Map.copyOf(collector.durations), List.copyOf(differences)));
        }

        Set<String> completedIds = cases.stream().map(FullEvaluationResult.Case::caseId)
                .collect(java.util.stream.Collectors.toSet());
        List<String> unexecuted = selected.stream().map(EvaluationCase::caseId)
                .filter(id -> !completedIds.contains(id)).toList();

        Map<String, FullEvaluationResult.Latency> latencies = new LinkedHashMap<>();
        durationSamples.forEach((segment, samples) -> latencies.put(segment, percentile(samples)));
        FullEvaluationResult.Count parsing = new FullEvaluationResult.Count(parsingCorrect, parsingTotal);
        FullEvaluationResult.Count rules = new FullEvaluationResult.Count(ruleCorrect, ruleTotal);
        FullEvaluationResult.Count tools = new FullEvaluationResult.Count(toolsSuccess, toolsTotal);
        FullEvaluationResult.Count facts = new FullEvaluationResult.Count(factsSupported, factsChecked);
        FullEvaluationResult.UsageSummary usage = summarizeUsage(cases);
        boolean passed = !anomaly && !budget.stopped() && unexecuted.isEmpty()
                && parsing.total() > 0 && rules.total() > 0
                && tools.total() > 0 && facts.total() > 0
                && parsing.ratio() >= .98 && rules.ratio() == 1.0
                && tools.ratio() >= TOOL_THRESHOLD && facts.ratio() >= FACT_THRESHOLD;
        return new FullEvaluationResult(dataset.suiteId(), dataset.version(), OfflineEvaluationRunner.RULE_VERSION,
                "full", gitCommit, Instant.now(), List.copyOf(cases),
                parsing, rules, tools, facts, usage, Map.copyOf(latencies), budget.snapshot(),
                List.copyOf(unexecuted), passed);
    }

    /** 仅汇总 provider 实际返回的数字；缺失 usage 或 total 都单独计数。 */
    private static FullEvaluationResult.UsageSummary summarizeUsage(List<FullEvaluationResult.Case> cases) {
        long calls = 0, complete = 0, incomplete = 0, input = 0, output = 0, reportedTotal = 0;
        long missingTotal = 0;
        for (FullEvaluationResult.Case item : cases) {
            for (FullEvaluationResult.ModelCallUsage usage : item.modelCalls()) {
                calls++;
                if (usage.inputTokens() == null || usage.outputTokens() == null) {
                    incomplete++;
                } else {
                    complete++;
                    input += usage.inputTokens();
                    output += usage.outputTokens();
                }
                if (usage.totalTokens() == null) {
                    missingTotal++;
                } else {
                    reportedTotal += usage.totalTokens();
                }
            }
        }
        return new FullEvaluationResult.UsageSummary(calls, complete, incomplete,
                input, output, reportedTotal, missingTotal);
    }

    /** 小样本也使用最近秩，避免不同统计包的插值约定改变报告。 */
    public static FullEvaluationResult.Latency percentile(List<Long> values) {
        if (values.isEmpty()) {
            return new FullEvaluationResult.Latency(0, null, null);
        }
        List<Long> sorted = values.stream().sorted().toList();
        return new FullEvaluationResult.Latency(sorted.size(),
                sorted.get((int) Math.ceil(.50 * sorted.size()) - 1),
                sorted.get((int) Math.ceil(.95 * sorted.size()) - 1));
    }

    private static Difference error(EvaluationCase item, String message) {
        return new Difference(item.caseId(), "ERROR", "full.execution", null,
                "真实 Agent 分析与完整遥测", message, null, null, "/result");
    }

    /** 每笔独立收集回调，避免并发分析或异常后的样本互相污染。 */
    private static final class Collector implements AnalysisProgressListener {
        final ModelBudget budget;
        final Map<String, Long> durations = new LinkedHashMap<>();
        final List<FullEvaluationResult.ModelCallUsage> usages = new ArrayList<>();
        int modelCalls;
        boolean missingUsage;
        boolean pendingCall;

        Collector(ModelBudget budget) {
            this.budget = budget;
        }

        @Override
        public void onSegmentTiming(String segment, long durationNanos) {
            if (SEGMENTS.contains(segment)) {
                addTiming(segment, durationNanos);
            }
        }

        @Override
        public void onModelUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
            modelCalls++;
            pendingCall = false;
            usages.add(new FullEvaluationResult.ModelCallUsage(inputTokens, outputTokens, totalTokens));
            if (inputTokens == null || outputTokens == null) {
                missingUsage = true;
            }
            budget.settle(inputTokens, outputTokens);
        }

        @Override
        public void beforeModelCall(Integer requestMaxOutputTokens) {
            budget.beforeCall(requestMaxOutputTokens);
            pendingCall = true;
        }

        @Override
        public void onModelFailure(String errorType) {
            missingUsage = true;
            if (pendingCall) {
                modelCalls++;
                usages.add(new FullEvaluationResult.ModelCallUsage(null, null, null));
                pendingCall = false;
            }
            budget.failCall(errorType);
        }

        void addTiming(String segment, long nanos) {
            durations.merge(segment, Math.max(0L, nanos), Long::sum);
        }
    }
}

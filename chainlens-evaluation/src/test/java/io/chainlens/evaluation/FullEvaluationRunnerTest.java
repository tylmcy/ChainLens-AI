package io.chainlens.evaluation;

import java.nio.file.Path;
import java.nio.file.Files;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.chainlens.agent.AnalysisProgressListener;
import io.chainlens.agent.AnalysisService;
import io.chainlens.agent.TemplateReportGenerator;
import io.chainlens.chain.TxFactsAssembler;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.ToolCallStep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FullEvaluationRunnerTest {

    private static final Path FIXTURES = Path.of("../eval/fixtures");
    private static final Path MANIFEST = Path.of("../eval/cases/transaction-eval-v1.json");
    private static final String CASE_ID = "anvil-finite-approval";

    private final EvaluationDataset dataset = EvaluationDatasetLoader.load(MANIFEST, FIXTURES);
    private final EvaluationCase item = dataset.cases().stream()
            .filter(candidate -> CASE_ID.equals(candidate.caseId())).findFirst().orElseThrow();

    private static ModelBudget.Config budget() {
        return new ModelBudget.Config("mock", "mock-model", LocalDate.of(2026, 10, 19),
                "test-context", 100, 20, new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("1"));
    }

    /** 通过真实冻结 RPC 和生产 parser/规则生成模拟 Agent 的机器字段。 */
    private FullNetworkRouter router(boolean toolStep, boolean validFactToken) {
        return router(toolStep, validFactToken, true);
    }

    private FullNetworkRouter router(boolean toolStep, boolean validFactToken, boolean toolSuccess) {
        return router(toolStep, validFactToken, toolSuccess, 10, 5);
    }

    private FullNetworkRouter router(boolean toolStep, boolean validFactToken, boolean toolSuccess,
                                     int inputTokens, int outputTokens) {
        return router(toolStep, validFactToken, toolSuccess, inputTokens, outputTokens, 1);
    }

    private FullNetworkRouter router(boolean toolStep, boolean validFactToken, boolean toolSuccess,
                                     int inputTokens, int outputTokens, int calls) {
        FixtureEthRpcClient rpc = new FixtureEthRpcClient(item.caseId(), item.txHash(),
                FIXTURES.resolve(item.fixtureDir()));
        AnalysisService service = (txHash, listener) -> {
            var facts = new TxFactsAssembler(rpc).assemble(txHash);
            var findings = EvaluationRuleSet.create().evaluate(facts);
            listener.onSegmentTiming("rpc", 11);
            listener.onSegmentTiming("parsing", 13);
            listener.onSegmentTiming("rules", 17);
            listener.onSegmentTiming("llm", 19);
            for (int i = 0; i < calls; i++) {
                listener.beforeModelCall(20);
                listener.onModelUsage(inputTokens, outputTokens, inputTokens + outputTokens);
            }
            var report = new AnalysisReport(validFactToken ? "交易 " + txHash + " 已完成" : "交易已完成",
                    facts.receipt().isSuccess() ? io.chainlens.model.AnalysisStatus.SUCCESS
                            : io.chainlens.model.AnalysisStatus.FAILED,
                    List.of(), findings, List.of(), List.of(TemplateReportGenerator.DISCLAIMER));
            List<ToolCallStep> steps = toolStep
                    ? List.of(new ToolCallStep("getCaseFacts", "{}", Instant.now(), 1, toolSuccess,
                            toolSuccess ? "成功" : "失败"))
                    : List.of();
            return new AnalysisResult("test", txHash, facts.network(), report, facts, findings,
                    steps, true, false, false, 1, Instant.now());
        };
        return new FullNetworkRouter(Map.of(item.chainId(), new FullNetworkRouter.Endpoint(rpc, service)));
    }

    @Test
    void routesOneCaseAndScoresToolsFactsAndSegments() {
        var result = new FullEvaluationRunner(router(true, true)).run(dataset, Set.of(CASE_ID), budget());

        assertThat(result.cases()).hasSize(1);
        assertThat(result.cases().get(0).passed()).isTrue();
        assertThat(result.parsingAccuracy()).isEqualTo(new FullEvaluationResult.Count(15, 15));
        assertThat(result.ruleAccuracy()).isEqualTo(new FullEvaluationResult.Count(7, 7));
        assertThat(result.toolSuccessRate()).isEqualTo(new FullEvaluationResult.Count(1, 1));
        assertThat(result.factConsistencyRate()).isEqualTo(new FullEvaluationResult.Count(1, 1));
        assertThat(result.budget().verifiedCostUsd()).isEqualByComparingTo("0.000015");
        assertThat(result.modelUsage()).isEqualTo(new FullEvaluationResult.UsageSummary(
                1, 1, 0, 10, 5, 15, 0));
        assertThat(result.cases().get(0).modelCalls())
                .containsExactly(new FullEvaluationResult.ModelCallUsage(10, 5, 15));
        assertThat(result.latencyNanos().get("rpc")).isEqualTo(new FullEvaluationResult.Latency(1, 11L, 11L));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void emptyToolOrZeroFactTokenIsAnomaly() {
        var noTool = new FullEvaluationRunner(router(false, true)).run(dataset, Set.of(CASE_ID), budget());
        var failedTool = new FullEvaluationRunner(router(true, true, false))
                .run(dataset, Set.of(CASE_ID), budget());
        var noFact = new FullEvaluationRunner(router(true, false)).run(dataset, Set.of(CASE_ID), budget());

        assertThat(noTool.passed()).isFalse();
        assertThat(noTool.toolSuccessRate().total()).isZero();
        assertThat(noTool.cases().get(0).differences()).anySatisfy(diff ->
                assertThat(diff.actual()).contains("工具调用"));
        assertThat(failedTool.toolSuccessRate()).isEqualTo(new FullEvaluationResult.Count(0, 1));
        assertThat(failedTool.passed()).isFalse();
        assertThat(noFact.passed()).isFalse();
        assertThat(noFact.factConsistencyRate().total()).isZero();
        assertThat(noFact.cases().get(0).differences()).anySatisfy(diff ->
                assertThat(diff.actual()).contains("token 数为零"));
    }

    @Test
    void wrongRpcChainIsRejectedBeforeModelAnalysis() {
        var mainnet = dataset.cases().stream().filter(candidate -> candidate.chainId() == 1)
                .findFirst().orElseThrow();
        assertThatThrownBy(() -> router(true, true).preflight(List.of(item, mainnet)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chainId=1");
        FixtureEthRpcClient wrongRpc = new FixtureEthRpcClient(mainnet.caseId(), mainnet.txHash(),
                FIXTURES.resolve(mainnet.fixtureDir()));
        var wrong = new FullNetworkRouter(Map.of(item.chainId(),
                new FullNetworkRouter.Endpoint(wrongRpc, (hash, listener) -> {
                    throw new AssertionError("错误网络不得调用模型");
                })));
        assertThatThrownBy(() -> wrong.preflight(List.of(item)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("网络不匹配");
    }

    @Test
    void guardedFactsRejectWrongChainBeforeAgentCanCallModel() {
        FixtureEthRpcClient rpc = new FixtureEthRpcClient(item.caseId(), item.txHash(),
                FIXTURES.resolve(item.fixtureDir()));
        var provider = new NetworkGuardedFactsProvider(1L, new TxFactsAssembler(rpc));

        assertThatThrownBy(() -> provider.assemble(item.txHash()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chainId=1");
    }

    @Test
    void nearestRankPercentileHandlesEmptyAndSmallSamples() {
        assertThat(FullEvaluationRunner.percentile(List.of()))
                .isEqualTo(new FullEvaluationResult.Latency(0, null, null));
        assertThat(FullEvaluationRunner.percentile(List.of(90L, 10L, 30L, 20L)))
                .isEqualTo(new FullEvaluationResult.Latency(4, 20L, 90L));
    }

    @Test
    void retainsEachProviderUsageAndAggregatesMultipleCalls() {
        var result = new FullEvaluationRunner(router(true, true, true, 10, 5, 2))
                .run(dataset, Set.of(CASE_ID), budget());

        assertThat(result.modelUsage()).isEqualTo(new FullEvaluationResult.UsageSummary(
                2, 2, 0, 20, 10, 30, 0));
        assertThat(result.cases().get(0).modelCalls()).containsExactly(
                new FullEvaluationResult.ModelCallUsage(10, 5, 15),
                new FullEvaluationResult.ModelCallUsage(10, 5, 15));
        assertThat(result.budget().verifiedCostUsd()).isEqualByComparingTo("0.000030");
    }

    @Test
    void missingProviderUsageStaysUnavailableInsteadOfBecomingZero() {
        FixtureEthRpcClient rpc = new FixtureEthRpcClient(item.caseId(), item.txHash(),
                FIXTURES.resolve(item.fixtureDir()));
        AnalysisService service = (txHash, listener) -> {
            listener.beforeModelCall(20);
            listener.onModelUsage(null, 5, null);
            throw new AssertionError("缺失 usage 必须中止，不得正常返回");
        };
        var result = new FullEvaluationRunner(new FullNetworkRouter(Map.of(item.chainId(),
                new FullNetworkRouter.Endpoint(rpc, service))))
                .run(dataset, Set.of(CASE_ID), budget());

        assertThat(result.passed()).isFalse();
        assertThat(result.modelUsage()).isEqualTo(new FullEvaluationResult.UsageSummary(
                1, 0, 1, 0, 0, 0, 1));
        assertThat(result.cases().get(0).modelCalls())
                .containsExactly(new FullEvaluationResult.ModelCallUsage(null, 5, null));
        assertThat(result.budget().unresolvedReserveUsd()).isPositive();
    }

    @Test
    void exactBudgetStopsBeforeNextSelectedCase() {
        ModelBudget.Config limit = new ModelBudget.Config("mock", "mock-model",
                LocalDate.of(2026, 10, 19), "test-context", 100, 20,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.000120"));
        var result = new FullEvaluationRunner(router(true, true, true, 100, 20))
                .run(dataset, Set.of(CASE_ID, "anvil-interaction-with-eoa"), limit);

        assertThat(result.cases()).extracting(FullEvaluationResult.Case::caseId).containsExactly(CASE_ID);
        assertThat(result.unexecutedCaseIds()).containsExactly("anvil-interaction-with-eoa");
        assertThat(result.budget().verifiedCostUsd()).isEqualByComparingTo("0.000120");
        assertThat(result.budget().stopped()).isTrue();
        assertThat(result.passed()).isFalse();
    }

    @Test
    void fullReportDoesNotCopyExceptionMessageThatMayContainRpcCredentials(@TempDir Path directory)
            throws Exception {
        FixtureEthRpcClient rpc = new FixtureEthRpcClient(item.caseId(), item.txHash(),
                FIXTURES.resolve(item.fixtureDir()));
        AnalysisService failing = (txHash, listener) -> {
            throw new IllegalStateException("https://rpc.example?api_key=SECRET_SENTINEL");
        };
        var router = new FullNetworkRouter(Map.of(item.chainId(),
                new FullNetworkRouter.Endpoint(rpc, failing)));
        var result = new FullEvaluationRunner(router).run(dataset, Set.of(CASE_ID), budget());
        var written = new FullEvaluationReportWriter().write(result, directory);

        assertThat(result.passed()).isFalse();
        assertThat(Files.readString(written.json())).doesNotContain("SECRET_SENTINEL");
        assertThat(Files.readString(written.markdown())).doesNotContain("SECRET_SENTINEL");
    }

    @Test
    void fullReportWritesSameBudgetAndCasesToJsonAndMarkdown(@TempDir Path directory) throws Exception {
        var result = new FullEvaluationRunner(router(true, true)).run(dataset, Set.of(CASE_ID), budget(),
                "0123456789012345678901234567890123456789");
        var written = new FullEvaluationReportWriter().write(result, directory);
        var json = new ObjectMapper().readTree(written.json().toFile());
        var markdown = Files.readString(written.markdown());

        assertThat(json.path("budget").path("verifiedCostUsd").decimalValue())
                .isEqualByComparingTo("0.000015");
        assertThat(json.path("cases").size()).isEqualTo(1);
        assertThat(json.path("modelUsage").path("inputTokens").asLong()).isEqualTo(10);
        assertThat(json.path("cases").get(0).path("modelCalls").get(0).path("outputTokens").asInt())
                .isEqualTo(5);
        assertThat(json.path("gitCommit").asText())
                .isEqualTo("0123456789012345678901234567890123456789");
        assertThat(markdown).contains("0.000015", CASE_ID, "工具调用成功率", "事实一致率",
                "模型调用 1：输入 10，输出 5，provider total 15 token");
    }
}

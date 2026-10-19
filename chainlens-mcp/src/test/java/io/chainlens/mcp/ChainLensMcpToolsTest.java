package io.chainlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import io.chainlens.agent.AnalysisService;
import io.chainlens.chain.TxFactsProvider;
import io.chainlens.chain.parse.SelectorRegistry;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.EthBlock;
import io.chainlens.model.EthReceipt;
import io.chainlens.model.EthTransaction;
import io.chainlens.model.Finding;
import io.chainlens.model.Network;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.rule.FailedTransactionRule;

class ChainLensMcpToolsTest {

    private static final String HASH_A =
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B =
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void rejectsInvalidInputsBeforeCallingAnyDownstreamPort() {
        AnalysisService analysis = mock(AnalysisService.class);
        TxFactsProvider facts = mock(TxFactsProvider.class);
        SelectorRegistry selectors = mock(SelectorRegistry.class);
        KnowledgeSearch knowledge = mock(KnowledgeSearch.class);
        ChainLensMcpTools tools = tools(analysis, facts, selectors, knowledge, new Semaphore(2));

        assertInvalid(() -> tools.analyzeTransaction("0x1234"));
        assertInvalid(() -> tools.getCaseFacts("not-a-hash"));
        assertInvalid(() -> tools.getRiskFindings(null));
        assertInvalid(() -> tools.resolveSelector("0x123"));
        assertInvalid(() -> tools.searchKnowledge("  "));
        assertInvalid(() -> tools.searchKnowledge("x".repeat(201)));

        verifyNoInteractions(analysis, facts, selectors, knowledge);
    }

    @Test
    void completeAnalysisPreservesEvidenceAndForcesMcpResultToNotPersisted() {
        TxFacts txFacts = facts(HASH_A, "0x0");
        Finding finding = new FailedTransactionRule().evaluate(txFacts).getFirst();
        AnalysisReport report = new AnalysisReport(
                "固定报告", AnalysisStatus.FAILED, List.of(), List.of(finding),
                List.of(new AnalysisReport.Source("CHAIN", "receipt", "/result/status")),
                List.of("不构成投资建议"));
        AnalysisResult downstream = new AnalysisResult(
                "analysis-1", HASH_A, Network.ANVIL, report, txFacts, List.of(finding), List.of(),
                false, false, true, 12, Instant.EPOCH);
        AnalysisService analysis = mock(AnalysisService.class);
        when(analysis.analyze(org.mockito.ArgumentMatchers.eq(HASH_A), org.mockito.ArgumentMatchers.any()))
                .thenReturn(downstream);
        TxFactsProvider facts = mock(TxFactsProvider.class);

        McpAnalysisResultView result = tools(analysis, facts, mock(SelectorRegistry.class),
                mock(KnowledgeSearch.class), new Semaphore(1)).analyzeTransaction(HASH_A);

        assertThat(result.persisted()).isFalse();
        assertThat(result.report().findings()).extracting(McpFindingView::ruleId)
                .containsExactly(finding.ruleId());
        assertThat(result.findings().getFirst().ruleId()).isEqualTo("TX-FAILED");
        assertThat(result.findings().getFirst().confidence()).isEqualTo(1.0);
        assertThat(result.findings().getFirst().evidence().rawJsonRef().jsonPointer())
                .isEqualTo("/result/status");
        assertThat(result.report().sources()).extracting(McpAnalysisResultView.SourceView::type)
                .containsExactly("CHAIN");
        verifyNoInteractions(facts);
    }

    @Test
    void caseFactsReturnsTheExactForensicRecord() {
        TxFacts expected = facts(HASH_A, "0x1");
        TxFactsProvider provider = mock(TxFactsProvider.class);
        when(provider.assemble(HASH_A)).thenReturn(expected);

        McpTxFactsView actual = tools(mock(AnalysisService.class), provider, mock(SelectorRegistry.class),
                mock(KnowledgeSearch.class), new Semaphore(1)).getCaseFacts(HASH_A);

        assertThat(actual.transaction().hash()).isEqualTo(expected.transaction().hash());
        assertThat(actual.receipt().rawJson()).isEqualTo(expected.receipt().rawJson());
    }

    @Test
    void riskFindingsOnlyComeFromTheDeterministicRuleEngine() {
        TxFactsProvider provider = mock(TxFactsProvider.class);
        when(provider.assemble(HASH_A)).thenReturn(facts(HASH_A, "0x0"));
        when(provider.assemble(HASH_B)).thenReturn(facts(HASH_B, "0x1"));
        KnowledgeSearch knowledge = mock(KnowledgeSearch.class);
        AnalysisService analysis = mock(AnalysisService.class);
        ChainLensMcpTools tools = new ChainLensMcpTools(analysis, provider,
                new RuleEngine(List.of(new FailedTransactionRule())), mock(SelectorRegistry.class),
                knowledge, new Semaphore(1), null, "streamable-http");

        assertThat(tools.getRiskFindings(HASH_A).findings())
                .extracting(McpFindingView::ruleId).containsExactly("TX-FAILED");
        assertThat(tools.getRiskFindings(HASH_B).findings()).isEmpty();
        verifyNoInteractions(analysis, knowledge);
    }

    @Test
    void selectorResolutionKeepsAllCollisionCandidatesAndReportsMisses() {
        SelectorRegistry selectors = selector -> switch (selector) {
            case "0x095ea7b3" -> List.of("approve(address,uint256)", "watch_tg_invmru_2f69f1b(address,address)");
            default -> List.of();
        };
        ChainLensMcpTools tools = tools(mock(AnalysisService.class), mock(TxFactsProvider.class),
                selectors, mock(KnowledgeSearch.class), new Semaphore(1));

        McpSelectorResolution collision = tools.resolveSelector("0x095EA7B3");
        McpSelectorResolution missing = tools.resolveSelector("0x00000000");

        assertThat(collision.status()).isEqualTo(McpSelectorResolution.Status.AMBIGUOUS);
        assertThat(collision.candidates()).hasSize(2);
        assertThat(missing.status()).isEqualTo(McpSelectorResolution.Status.UNRESOLVED);
        assertThat(missing.candidates()).isEmpty();
    }

    @Test
    void knowledgeSearchPreservesHitEmptyAndUnavailableStates() {
        KnowledgeSearch knowledge = mock(KnowledgeSearch.class);
        KnowledgeSearch.KnowledgeHit hit = new KnowledgeSearch.KnowledgeHit(
                "无限授权解释", "knowledge/rules.md", "授权风险",
                List.of("ERC20-UNLIMITED-APPROVAL"), 0.91);
        when(knowledge.search("无限授权")).thenReturn(KnowledgeSearch.SearchResult.available(List.of(hit)));
        when(knowledge.search("不存在")).thenReturn(KnowledgeSearch.SearchResult.available(List.of()));
        when(knowledge.search("未就绪")).thenReturn(KnowledgeSearch.SearchResult.unavailable(
                KnowledgeSearch.Availability.NOT_READY, "向量索引尚未建立"));
        ChainLensMcpTools tools = tools(mock(AnalysisService.class), mock(TxFactsProvider.class),
                mock(SelectorRegistry.class), knowledge, new Semaphore(1));

        assertThat(tools.searchKnowledge("无限授权").hits()).containsExactly(hit);
        assertThat(tools.searchKnowledge("不存在").hits()).isEmpty();
        assertThat(tools.searchKnowledge("未就绪").status()).isEqualTo(KnowledgeSearch.Availability.NOT_READY);
    }

    @Test
    void exposesExactlyFiveReadOnlySchemaGeneratingTools() {
        List<java.lang.reflect.Method> methods = Arrays.stream(ChainLensMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .toList();

        assertThat(methods).extracting(method -> method.getAnnotation(McpTool.class).name())
                .containsExactlyInAnyOrder(
                        "analyzeTransaction", "getCaseFacts", "getRiskFindings",
                        "resolveSelector", "searchKnowledge");
        assertThat(methods).allSatisfy(method -> {
            McpTool tool = method.getAnnotation(McpTool.class);
            assertThat(tool.generateOutputSchema()).isTrue();
            assertThat(tool.annotations().readOnlyHint()).isTrue();
            assertThat(tool.annotations().destructiveHint()).isFalse();
            assertThat(method.getParameterCount()).isEqualTo(1);
            McpToolParam parameter = method.getParameters()[0].getAnnotation(McpToolParam.class);
            assertThat(parameter).isNotNull();
            assertThat(parameter.required()).isTrue();
        });
        assertThat(methods).noneMatch(method -> method.getName().matches("(?i).*(send|sign|write|broadcast).*"));
    }

    @Test
    void concurrentCallsDoNotShareCurrentTransactionState() throws Exception {
        CyclicBarrier bothInsideProvider = new CyclicBarrier(2);
        TxFactsProvider provider = hash -> {
            try {
                bothInsideProvider.await();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
            return facts(hash, "0x1");
        };
        ChainLensMcpTools tools = tools(mock(AnalysisService.class), provider,
                mock(SelectorRegistry.class), mock(KnowledgeSearch.class), new Semaphore(2));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> tools.getCaseFacts(HASH_A));
            var second = executor.submit(() -> tools.getCaseFacts(HASH_B));
            assertThat(first.get().transaction().hash()).isEqualTo(HASH_A);
            assertThat(second.get().transaction().hash()).isEqualTo(HASH_B);
        }
    }

    @Test
    void releasesSharedPermitAfterSuccessFailureAndCancellation() {
        assertPermitReleasedAfter(hash -> facts(hash, "0x1"), null);
        assertPermitReleasedAfter(hash -> {
            throw new IllegalStateException("boom");
        }, McpErrorCode.INTERNAL_ERROR);
        assertPermitReleasedAfter(hash -> {
            throw new CancellationException("cancelled");
        }, McpErrorCode.INTERNAL_ERROR);
    }

    @Test
    void rejectsImmediatelyWhenSharedConcurrencyLimitIsExhausted() {
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquireUninterruptibly();
        ChainLensMcpTools tools = tools(mock(AnalysisService.class), hash -> facts(hash, "0x1"),
                mock(SelectorRegistry.class), mock(KnowledgeSearch.class), semaphore);

        assertThatThrownBy(() -> tools.getCaseFacts(HASH_A))
                .isInstanceOfSatisfying(ChainLensMcpException.class,
                        error -> assertThat(error.code()).isEqualTo(McpErrorCode.TOO_MANY_REQUESTS));
        assertThat(semaphore.availablePermits()).isZero();
        semaphore.release();
    }

    private static void assertPermitReleasedAfter(TxFactsProvider provider, McpErrorCode expectedError) {
        Semaphore semaphore = new Semaphore(1);
        ChainLensMcpTools tools = tools(mock(AnalysisService.class), provider,
                mock(SelectorRegistry.class), mock(KnowledgeSearch.class), semaphore);
        if (expectedError == null) {
            tools.getCaseFacts(HASH_A);
        } else {
            assertThatThrownBy(() -> tools.getCaseFacts(HASH_A))
                    .isInstanceOfSatisfying(ChainLensMcpException.class,
                            error -> assertThat(error.code()).isEqualTo(expectedError));
        }
        assertThat(semaphore.availablePermits()).isEqualTo(1);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ChainLensMcpException.class,
                        error -> assertThat(error.code()).isEqualTo(McpErrorCode.INVALID_INPUT));
    }

    private static ChainLensMcpTools tools(AnalysisService analysis,
                                           TxFactsProvider facts,
                                           SelectorRegistry selectors,
                                           KnowledgeSearch knowledge,
                                           Semaphore semaphore) {
        return new ChainLensMcpTools(analysis, facts,
                new RuleEngine(List.of(new FailedTransactionRule())), selectors, knowledge,
                semaphore, null, "streamable-http");
    }

    /** 构造最小但包含原始 JSON 定位所需字段的固定交易案卷。 */
    private static TxFacts facts(String txHash, String status) {
        String from = "0x1111111111111111111111111111111111111111";
        String to = "0x2222222222222222222222222222222222222222";
        EthTransaction transaction = new EthTransaction(
                txHash, from, to, BigInteger.ZERO, BigInteger.ONE, null, null,
                BigInteger.valueOf(21_000), BigInteger.ZERO, 0, "0x", BigInteger.ONE, "{\"hash\":\"" + txHash + "\"}");
        EthReceipt receipt = new EthReceipt(
                txHash, status, BigInteger.valueOf(21_000), BigInteger.ONE,
                null, List.of(), BigInteger.ONE, "{\"status\":\"" + status + "\"}");
        return new TxFacts(
                Network.ANVIL, transaction, receipt,
                new EthBlock(BigInteger.ONE, 1_700_000_000L, null, "{\"number\":\"0x1\"}"),
                BigInteger.valueOf(21_000), BigInteger.ZERO, BigInteger.valueOf(-21_000),
                BigInteger.ZERO, List.of(), List.of(), List.of(),
                new CalldataAnalysis(false, null, List.of(), null, CalldataAnalysis.Status.UNRESOLVED),
                ContractCodeInfo.unavailable(to, BigInteger.ONE, "fixture"), Instant.EPOCH);
    }
}

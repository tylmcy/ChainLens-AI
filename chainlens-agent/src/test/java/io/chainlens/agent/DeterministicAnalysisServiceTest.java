package io.chainlens.agent;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.chain.TxFactsProvider;
import io.chainlens.chain.rpc.RpcUnavailableException;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.rule.FailedTransactionRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicAnalysisServiceTest {

    /** 事件收集器：按顺序记录回调。 */
    private static final class EventCollector implements AnalysisProgressListener {

        final List<String> events = new ArrayList<>();

        @Override
        public void onStage(String stage, String message) {
            events.add("stage:" + stage);
        }

        @Override
        public void onRulesCompleted(List<Finding> findings) {
            events.add("rules:" + findings.size());
        }

        @Override
        public void onReportCompleted(AnalysisReport report) {
            events.add("report:" + report.status());
        }

        @Override
        public void onAnalysisCompleted(AnalysisResult result) {
            events.add("completed");
        }

        @Override
        public void onAnalysisFailed(Throwable error) {
            events.add("failed:" + error.getClass().getSimpleName());
        }
    }

    private static DeterministicAnalysisService service(TxFacts facts) {
        return new DeterministicAnalysisService(
                hash -> facts,
                new RuleEngine(List.of(new FailedTransactionRule())),
                new TemplateReportGenerator());
    }

    @Test
    @DisplayName("确定性链路：取证 → 规则 → 模板报告，事件顺序正确，标记 LLM 降级")
    void deterministicPipeline() {
        TxFacts facts = AgTxFactsFactory.create().withStatus("0x0").build();
        EventCollector collector = new EventCollector();

        AnalysisResult result = service(facts).analyze(facts.transaction().hash(), collector);

        assertThat(result.llmUsed()).isFalse();
        assertThat(result.degraded()).isTrue();
        assertThat(result.persisted()).isFalse();
        assertThat(result.findings()).extracting(Finding::ruleId).containsExactly("TX-FAILED");
        assertThat(result.report().limitations().get(0)).isEqualTo(TemplateReportGenerator.DISCLAIMER);
        assertThat(collector.events)
                .containsExactly("stage:forensics", "stage:rules", "rules:1",
                        "stage:report", "report:FAILED", "completed");
        assertThat(result.analysisId()).isNotBlank();
    }

    @Test
    @DisplayName("RPC 不可达：onAnalysisFailed 回调 + 异常上抛（不吞错不伪造）")
    void rpcFailurePropagates() {
        DeterministicAnalysisService failing = new DeterministicAnalysisService(
                hash -> {
                    throw new RpcUnavailableException("节点不可达");
                },
                new RuleEngine(List.of(new FailedTransactionRule())),
                new TemplateReportGenerator());
        EventCollector collector = new EventCollector();

        assertThatThrownBy(() -> failing.analyze("0xabc", collector))
                .isInstanceOf(RpcUnavailableException.class);
        assertThat(collector.events).containsExactly("stage:forensics", "failed:RpcUnavailableException");
    }
}

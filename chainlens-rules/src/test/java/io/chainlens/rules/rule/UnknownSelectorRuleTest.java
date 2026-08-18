package io.chainlens.rules.rule;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.Severity;
import io.chainlens.rules.TxFactsFactory;

import static org.assertj.core.api.Assertions.assertThat;

class UnknownSelectorRuleTest {

    private final UnknownSelectorRule rule = new UnknownSelectorRule();

    @Test
    @DisplayName("正例：UNRESOLVED（无候选）→ LOW / 0.8，描述如实说明")
    void unresolvedSelectorTriggers() {
        var facts = TxFactsFactory.create()
                .withCalldata(new CalldataAnalysis(true, "0xdeadbeef", List.of(), null,
                        CalldataAnalysis.Status.UNRESOLVED))
                .build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("UNKNOWN-SELECTOR");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.LOW);
        assertThat(findings.get(0).confidence()).isEqualTo(0.8);
        assertThat(findings.get(0).description()).contains("无任何已知候选");
    }

    @Test
    @DisplayName("正例：AMBIGUOUS（多候选）→ 触发并列出全部候选，不武断选一")
    void ambiguousSelectorTriggersWithCandidates() {
        var facts = TxFactsFactory.create()
                .withCalldata(new CalldataAnalysis(true, "0x11111111",
                        List.of("foo()", "bar(uint256)"), null, CalldataAnalysis.Status.AMBIGUOUS))
                .build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).description()).contains("foo()", "bar(uint256)");
    }

    @Test
    @DisplayName("反例：RESOLVED（唯一候选）不触发")
    void resolvedSelectorDoesNotTrigger() {
        var facts = TxFactsFactory.create()
                .withCalldata(new CalldataAnalysis(true, "0xa9059cbb",
                        List.of("transfer(address,uint256)"), "transfer(address,uint256)",
                        CalldataAnalysis.Status.RESOLVED))
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }

    @Test
    @DisplayName("反例：本就没有 calldata 不触发")
    void noCalldataDoesNotTrigger() {
        assertThat(rule.evaluate(TxFactsFactory.create().build())).isEmpty();
    }
}

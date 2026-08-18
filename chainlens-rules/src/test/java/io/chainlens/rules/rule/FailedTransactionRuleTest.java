package io.chainlens.rules.rule;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.Severity;
import io.chainlens.rules.TxFactsFactory;

import static org.assertj.core.api.Assertions.assertThat;

class FailedTransactionRuleTest {

    private final FailedTransactionRule rule = new FailedTransactionRule();

    @Test
    @DisplayName("正例：status=0x0 → MEDIUM / 1.0，证据指向 /result/status")
    void failedStatusTriggers() {
        var facts = TxFactsFactory.create().withStatus("0x0").build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("TX-FAILED");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(findings.get(0).confidence()).isEqualTo(1.0);
        assertThat(findings.get(0).evidence().rawJsonRef().jsonPointer()).isEqualTo("/result/status");
    }

    @Test
    @DisplayName("反例：status=0x1 不触发")
    void successStatusDoesNotTrigger() {
        assertThat(rule.evaluate(TxFactsFactory.create().build())).isEmpty();
    }
}

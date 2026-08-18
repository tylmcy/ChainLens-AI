package io.chainlens.rules.rule;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.Severity;
import io.chainlens.model.TransferEvent;
import io.chainlens.rules.TxFactsFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManyTokenTransfersRuleTest {

    private final ManyTokenTransfersRule rule = new ManyTokenTransfersRule();

    private static List<TransferEvent> transfers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new TransferEvent(TxFactsFactory.TOKEN, TxFactsFactory.FROM,
                        "0x2222222222222222222222222222222222222222",
                        BigInteger.valueOf(1000), BigInteger.valueOf(i)))
                .toList();
    }

    @Test
    @DisplayName("边界：恰好 20 条不触发（> 20 才是 MEDIUM）")
    void exactlyMediumThresholdDoesNotTrigger() {
        var facts = TxFactsFactory.create().withTransfers(transfers(20)).build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }

    @Test
    @DisplayName("21 条 → MEDIUM / 0.8")
    void aboveMediumThresholdTriggersMedium() {
        var facts = TxFactsFactory.create().withTransfers(transfers(21)).build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(findings.get(0).confidence()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("边界：恰好 100 条仍是 MEDIUM（> 100 才是 HIGH）")
    void exactlyHighThresholdIsMedium() {
        var facts = TxFactsFactory.create().withTransfers(transfers(100)).build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    @DisplayName("101 条 → HIGH / 0.9")
    void aboveHighThresholdTriggersHigh() {
        var facts = TxFactsFactory.create().withTransfers(transfers(101)).build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(findings.get(0).confidence()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("自定义阈值（2/5）：3 条 MEDIUM，6 条 HIGH，2 条不触发")
    void customThresholds() {
        var custom = new ManyTokenTransfersRule(2, 5);

        assertThat(custom.evaluate(TxFactsFactory.create().withTransfers(transfers(2)).build())).isEmpty();
        assertThat(custom.evaluate(TxFactsFactory.create().withTransfers(transfers(3)).build()))
                .extracting(io.chainlens.model.Finding::severity).containsExactly(Severity.MEDIUM);
        assertThat(custom.evaluate(TxFactsFactory.create().withTransfers(transfers(6)).build()))
                .extracting(io.chainlens.model.Finding::severity).containsExactly(Severity.HIGH);
    }

    @Test
    @DisplayName("非法阈值（medium >= high 或 <= 0）构造失败")
    void invalidThresholdsRejected() {
        assertThatThrownBy(() -> new ManyTokenTransfersRule(0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManyTokenTransfersRule(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

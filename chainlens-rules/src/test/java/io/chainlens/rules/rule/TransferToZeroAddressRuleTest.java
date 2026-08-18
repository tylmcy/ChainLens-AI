package io.chainlens.rules.rule;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.Severity;
import io.chainlens.model.TransferEvent;
import io.chainlens.rules.TxFactsFactory;

import static org.assertj.core.api.Assertions.assertThat;

class TransferToZeroAddressRuleTest {

    private final TransferToZeroAddressRule rule = new TransferToZeroAddressRule();

    private static TransferEvent transfer(String to, long logIndex) {
        return new TransferEvent(TxFactsFactory.TOKEN, TxFactsFactory.FROM, to,
                BigInteger.valueOf(1000), BigInteger.valueOf(logIndex));
    }

    @Test
    @DisplayName("正例：转入零地址 → MEDIUM / 1.0")
    void transferToZeroTriggers() {
        var facts = TxFactsFactory.create()
                .withTransfers(List.of(transfer(TxFactsFactory.ZERO, 2)))
                .build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("TRANSFER-TO-ZERO-ADDRESS");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(findings.get(0).confidence()).isEqualTo(1.0);
        assertThat(findings.get(0).evidence().logIndex()).isEqualTo(BigInteger.valueOf(2));
    }

    @Test
    @DisplayName("反例：普通转账不触发")
    void normalTransferDoesNotTrigger() {
        var facts = TxFactsFactory.create()
                .withTransfers(List.of(transfer("0x2222222222222222222222222222222222222222", 0)))
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }
}

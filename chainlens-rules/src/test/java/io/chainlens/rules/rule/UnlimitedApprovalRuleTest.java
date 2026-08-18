package io.chainlens.rules.rule;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.rules.TxFactsFactory;

import static org.assertj.core.api.Assertions.assertThat;

class UnlimitedApprovalRuleTest {

    private final UnlimitedApprovalRule rule = new UnlimitedApprovalRule();

    private static ApprovalEvent approval(BigInteger value, long logIndex) {
        return new ApprovalEvent(TxFactsFactory.TOKEN, TxFactsFactory.FROM,
                "0x4444444444444444444444444444444444444444", value, BigInteger.valueOf(logIndex));
    }

    @Test
    @DisplayName("正例：value == 2^256-1 → HIGH / 1.0，证据带日志定位")
    void unlimitedApprovalTriggers() {
        var facts = TxFactsFactory.create()
                .withApprovals(List.of(approval(TxFactsFactory.TWO_256_MINUS_1, 1)))
                .build();

        List<Finding> findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        Finding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("ERC20-UNLIMITED-APPROVAL");
        assertThat(finding.severity()).isEqualTo(Severity.HIGH);
        assertThat(finding.confidence()).isEqualTo(1.0);
        assertThat(finding.evidence().logIndex()).isEqualTo(BigInteger.ONE);
        assertThat(finding.evidence().rpcMethod()).isEqualTo("eth_getTransactionReceipt");
    }

    @Test
    @DisplayName("边界反例：2^256-2 不算无限额度")
    void oneBelowUnlimitedDoesNotTrigger() {
        var facts = TxFactsFactory.create()
                .withApprovals(List.of(approval(TxFactsFactory.TWO_256_MINUS_1.subtract(BigInteger.ONE), 0)))
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }

    @Test
    @DisplayName("反例：普通额度授权")
    void normalApprovalDoesNotTrigger() {
        var facts = TxFactsFactory.create()
                .withApprovals(List.of(approval(BigInteger.valueOf(1000), 0)))
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }

    @Test
    @DisplayName("反例：无 Approval 事件")
    void noApprovalsDoesNotTrigger() {
        assertThat(rule.evaluate(TxFactsFactory.create().build())).isEmpty();
    }
}

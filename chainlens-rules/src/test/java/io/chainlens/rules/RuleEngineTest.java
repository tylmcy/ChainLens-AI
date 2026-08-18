package io.chainlens.rules;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.Finding;
import io.chainlens.rules.risk.BuiltInRiskAddressList;
import io.chainlens.rules.rule.FailedTransactionRule;
import io.chainlens.rules.rule.InteractionWithEoaRule;
import io.chainlens.rules.rule.ManyTokenTransfersRule;
import io.chainlens.rules.rule.RiskAddressHitRule;
import io.chainlens.rules.rule.TransferToZeroAddressRule;
import io.chainlens.rules.rule.UnknownSelectorRule;
import io.chainlens.rules.rule.UnlimitedApprovalRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    private static RuleEngine fullEngine() {
        return new RuleEngine(List.of(
                new UnlimitedApprovalRule(),
                new FailedTransactionRule(),
                new TransferToZeroAddressRule(),
                new InteractionWithEoaRule(),
                new UnknownSelectorRule(),
                new ManyTokenTransfersRule(),
                new RiskAddressHitRule(List.of(BuiltInRiskAddressList.fromClasspath(BuiltInRiskAddressList.DEFAULT_RESOURCE)))
        ));
    }

    @Test
    @DisplayName("全引擎执行：普通成功交易只有零命中；失败交易命中 TX-FAILED")
    void aggregatesFindingsAcrossRules() {
        assertThat(fullEngine().evaluate(TxFactsFactory.create().build())).isEmpty();

        List<Finding> findings = fullEngine().evaluate(TxFactsFactory.create().withStatus("0x0").build());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("TX-FAILED");
    }

    @Test
    @DisplayName("自检：重复 ruleId 构造即失败（fail-fast）")
    void duplicateRuleIdsRejected() {
        assertThatThrownBy(() -> new RuleEngine(List.of(new FailedTransactionRule(), new FailedTransactionRule())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TX-FAILED");
    }

    @Test
    @DisplayName("自检：null 条目与空列表拒绝")
    void nullAndEmptyRejected() {
        assertThatThrownBy(() -> new RuleEngine(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleEngine(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}

package io.chainlens.evaluation;

import java.util.List;

import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.risk.BuiltInRiskAddressList;
import io.chainlens.rules.rule.FailedTransactionRule;
import io.chainlens.rules.rule.InteractionWithEoaRule;
import io.chainlens.rules.rule.ManyTokenTransfersRule;
import io.chainlens.rules.rule.RiskAddressHitRule;
import io.chainlens.rules.rule.TransferToZeroAddressRule;
import io.chainlens.rules.rule.UnknownSelectorRule;
import io.chainlens.rules.rule.UnlimitedApprovalRule;

/**
 * 评测装配生产七规则；只决定实例和顺序，不复刻任何规则判断。
 * 风险地址列表与应用默认装配使用同一份 classpath 资源。
 */
public final class EvaluationRuleSet {

    private EvaluationRuleSet() {
    }

    public static RuleEngine create() {
        BuiltInRiskAddressList risks = BuiltInRiskAddressList.fromClasspath(
                BuiltInRiskAddressList.DEFAULT_RESOURCE);
        return new RuleEngine(List.of(
                new UnlimitedApprovalRule(),
                new FailedTransactionRule(),
                new TransferToZeroAddressRule(),
                new InteractionWithEoaRule(),
                new UnknownSelectorRule(),
                new ManyTokenTransfersRule(),
                new RiskAddressHitRule(List.of(risks))));
    }
}

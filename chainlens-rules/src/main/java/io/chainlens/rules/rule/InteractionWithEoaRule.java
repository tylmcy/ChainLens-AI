package io.chainlens.rules.rule;

import java.util.List;

import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.Rule;
import io.chainlens.rules.support.RuleEvidence;

/**
 * INTERACTION-WITH-EOA：to 地址在交易所在区块无合约代码 且 input 非空。
 * confidence=0.95（代码为空是确定事实，解释部分含说明），severity=LOW。
 *
 * <p>关键约束（设计文档 6.2）：历史代码不可查（available=false）时本规则**不触发**，
 * 不猜测——对应降级矩阵"eth_getCode 历史状态不可查 → 规则不触发，报告如实标注"。
 */
public final class InteractionWithEoaRule implements Rule {

    public static final String RULE_ID = "INTERACTION-WITH-EOA";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public List<Finding> evaluate(TxFacts facts) {
        ContractCodeInfo code = facts.contractCode();
        if (code == null || !code.available()) {
            return List.of(); // 不可查不猜测
        }
        if (code.hasCode()) {
            return List.of();
        }
        String input = facts.transaction().input();
        if (input == null || input.length() <= 2) {
            return List.of(); // 无 calldata，不是"疑似合约交互"
        }
        return List.of(new Finding(
                RULE_ID,
                Severity.LOW,
                0.95,
                "与无合约代码的地址交互",
                "交易目标地址在交易发生的区块没有任何合约代码，因此 calldata 不会被任何合约执行，"
                        + "资金（若有）将直接到达该 EOA。若你预期在与合约交互，请核对地址是否正确。",
                RuleEvidence.code(facts)
        ));
    }
}

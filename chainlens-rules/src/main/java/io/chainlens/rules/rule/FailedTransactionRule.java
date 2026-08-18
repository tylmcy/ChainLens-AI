package io.chainlens.rules.rule;

import java.util.List;

import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.Rule;
import io.chainlens.rules.support.RuleEvidence;

/**
 * TX-FAILED：receipt.status == 0x0。确定性判定，confidence=1.0，severity=MEDIUM。
 * V1 不做 revert 原因解析（需 trace 重放），写入 limitations（设计文档 6.2 / 6.5）。
 */
public final class FailedTransactionRule implements Rule {

    public static final String RULE_ID = "TX-FAILED";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public List<Finding> evaluate(TxFacts facts) {
        if (facts.receipt().isSuccess()) {
            return List.of();
        }
        return List.of(new Finding(
                RULE_ID,
                Severity.MEDIUM,
                1.0,
                "交易执行失败",
                "该交易在链上回执中标记为失败（status=0x0），所有状态变更均未生效。"
                        + "V1 不解析 revert 原因；失败本身未必是风险，请结合交易目的判断。",
                RuleEvidence.receipt(facts, "/result/status")
        ));
    }
}

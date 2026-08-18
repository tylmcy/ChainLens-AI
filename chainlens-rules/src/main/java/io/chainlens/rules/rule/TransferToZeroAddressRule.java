package io.chainlens.rules.rule;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.model.TransferEvent;
import io.chainlens.rules.Rule;
import io.chainlens.rules.support.RuleEvidence;

/**
 * TRANSFER-TO-ZERO-ADDRESS：Transfer 事件 to == 0x0。确定性判定，confidence=1.0，
 * severity=MEDIUM。只陈述事实，区分"合约销毁逻辑"与"疑似误转"（设计文档 6.2）。
 */
public final class TransferToZeroAddressRule implements Rule {

    public static final String RULE_ID = "TRANSFER-TO-ZERO-ADDRESS";

    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public List<Finding> evaluate(TxFacts facts) {
        List<Finding> findings = new ArrayList<>();
        for (TransferEvent transfer : facts.transfers()) {
            if (ZERO_ADDRESS.equals(transfer.to())) {
                findings.add(new Finding(
                        RULE_ID,
                        Severity.MEDIUM,
                        1.0,
                        "检测到向零地址转账",
                        "存在转入零地址（0x0000...0000）的 Token 转移，通常意味着销毁（burn）或误转。"
                                + "若该交易调用的是合约的销毁函数则属正常，否则请注意资金去向。",
                        RuleEvidence.receipt(facts, transfer.logIndex(), "/result/logs/" + transfer.logIndex())
                ));
            }
        }
        return findings;
    }
}

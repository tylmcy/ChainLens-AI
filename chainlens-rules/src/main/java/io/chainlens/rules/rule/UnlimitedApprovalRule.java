package io.chainlens.rules.rule;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.Rule;
import io.chainlens.rules.support.RuleEvidence;

/**
 * ERC20-UNLIMITED-APPROVAL：Approval 事件 value == 2^256-1。
 * 确定性判定，confidence=1.0，severity=HIGH（设计文档 6.2）。
 */
public final class UnlimitedApprovalRule implements Rule {

    public static final String RULE_ID = "ERC20-UNLIMITED-APPROVAL";

    private static final BigInteger UNLIMITED = new BigInteger("2").pow(256).subtract(BigInteger.ONE);

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public List<Finding> evaluate(TxFacts facts) {
        List<Finding> findings = new ArrayList<>();
        for (ApprovalEvent approval : facts.approvals()) {
            if (UNLIMITED.equals(approval.valueWei())) {
                findings.add(new Finding(
                        RULE_ID,
                        Severity.HIGH,
                        1.0,
                        "检测到无限额度授权",
                        "该交易将 ERC-20 代币授权额度设置为最大值（2^256-1）。"
                                + "授权对象（spender）可随时转走该代币的全部余额。"
                                + "若非你确认过的知名协议，请立即撤销授权（将额度改回 0）。",
                        RuleEvidence.receipt(facts, approval.logIndex(),
                                "/result/logs/" + logIndexInLogs(facts, approval.logIndex()))
                ));
            }
        }
        return findings;
    }

    /** 回执原始 JSON 中该事件日志的数组下标（按 logs 顺序扫描，安全兜底）。 */
    private static String logIndexInLogs(TxFacts facts, BigInteger logIndex) {
        for (int i = 0; i < facts.receipt().logs().size(); i++) {
            if (logIndex.equals(facts.receipt().logs().get(i).logIndex())) {
                return Integer.toString(i);
            }
        }
        return "0";
    }
}

package io.chainlens.rules.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.Finding;
import io.chainlens.model.Hexes;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.model.TransferEvent;
import io.chainlens.rules.Rule;
import io.chainlens.rules.risk.RiskAddressList;
import io.chainlens.rules.support.RuleEvidence;

/**
 * RISK-ADDRESS-HIT：交易参与方命中风险地址列表。
 *
 * <p>参与方精确定义（设计文档 6.2）：{tx.from, tx.to} ∪ 已解码 Transfer 的 from/to
 * ∪ 已解码 Approval 的 owner/spender；**不含** token 合约地址（log.address）。
 * 匹配按地址归一化（EIP-55 大小写不敏感）。
 *
 * <p>情报类规则：confidence=0.85（标签是第三方情报而非链上事实），
 * severity=HIGH，evidence 写明列表来源与条目。
 */
public final class RiskAddressHitRule implements Rule {

    public static final String RULE_ID = "RISK-ADDRESS-HIT";

    private final List<RiskAddressList> lists;

    public RiskAddressHitRule(List<RiskAddressList> lists) {
        if (lists == null || lists.isEmpty()) {
            throw new IllegalArgumentException("风险地址规则至少需要一个列表");
        }
        this.lists = List.copyOf(lists);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public List<Finding> evaluate(TxFacts facts) {
        List<Finding> findings = new ArrayList<>();
        for (String participant : participants(facts)) {
            for (RiskAddressList list : lists) {
                Optional<RiskAddressList.Entry> hit = list.find(participant);
                if (hit.isPresent()) {
                    RiskAddressList.Entry entry = hit.get();
                    findings.add(new Finding(
                            RULE_ID,
                            Severity.HIGH,
                            0.85,
                            "交易参与方命中风险地址列表",
                            "地址 " + entry.address() + " 命中风险列表（来源：" + entry.source()
                                    + "，标签：" + entry.label() + "）。"
                                    + "该标签属于第三方情报而非链上事实，请结合其他证据判断。",
                            RuleEvidence.transaction(facts, "/result/from")
                    ));
                }
            }
        }
        return findings;
    }

    /** 参与方集合（去重、归一化）；token 合约地址（log.address）刻意排除。 */
    private static Set<String> participants(TxFacts facts) {
        Set<String> participants = new LinkedHashSet<>();
        if (facts.transaction().from() != null) {
            participants.add(Hexes.normalizeAddress(facts.transaction().from()));
        }
        if (facts.transaction().to() != null) {
            participants.add(Hexes.normalizeAddress(facts.transaction().to()));
        }
        for (TransferEvent transfer : facts.transfers()) {
            participants.add(Hexes.normalizeAddress(transfer.from()));
            participants.add(Hexes.normalizeAddress(transfer.to()));
        }
        for (ApprovalEvent approval : facts.approvals()) {
            participants.add(Hexes.normalizeAddress(approval.owner()));
            participants.add(Hexes.normalizeAddress(approval.spender()));
        }
        return participants;
    }
}

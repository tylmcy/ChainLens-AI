package io.chainlens.rules.rule;

import java.util.ArrayList;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.Finding;
import io.chainlens.model.Evidence;
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
        for (Map.Entry<String, ParticipantLocation> participant : participants(facts).entrySet()) {
            for (RiskAddressList list : lists) {
                Optional<RiskAddressList.Entry> hit = list.find(participant.getKey());
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
                            participant.getValue().evidence(facts)
                    ));
                }
            }
        }
        return findings;
    }

    /** 按首次出现的位置保存参与方和真实证据；token 合约地址刻意排除。 */
    private static Map<String, ParticipantLocation> participants(TxFacts facts) {
        Map<String, ParticipantLocation> participants = new LinkedHashMap<>();
        if (facts.transaction().from() != null) {
            participants.putIfAbsent(Hexes.normalizeAddress(facts.transaction().from()),
                    new ParticipantLocation("/result/from", null, 0));
        }
        if (facts.transaction().to() != null) {
            participants.putIfAbsent(Hexes.normalizeAddress(facts.transaction().to()),
                    new ParticipantLocation("/result/to", null, 0));
        }
        for (TransferEvent transfer : facts.transfers()) {
            participants.putIfAbsent(Hexes.normalizeAddress(transfer.from()),
                    new ParticipantLocation(null, transfer.logIndex(), 1));
            participants.putIfAbsent(Hexes.normalizeAddress(transfer.to()),
                    new ParticipantLocation(null, transfer.logIndex(), 2));
        }
        for (ApprovalEvent approval : facts.approvals()) {
            participants.putIfAbsent(Hexes.normalizeAddress(approval.owner()),
                    new ParticipantLocation(null, approval.logIndex(), 1));
            participants.putIfAbsent(Hexes.normalizeAddress(approval.spender()),
                    new ParticipantLocation(null, approval.logIndex(), 2));
        }
        return participants;
    }

    /** 延迟解析证据位置：只为真正命中的地址查找原始日志。 */
    private record ParticipantLocation(String txPointer, BigInteger logIndex, int topicIndex) {
        private Evidence evidence(TxFacts facts) {
            return txPointer != null
                    ? RuleEvidence.transaction(facts, txPointer)
                    : eventEvidence(facts, logIndex, topicIndex);
        }
    }

    /** logIndex 是区块全局索引，先映射到本回执数组下标，不能直接拼进 JSON Pointer。 */
    private static Evidence eventEvidence(TxFacts facts, BigInteger logIndex, int topicIndex) {
        for (int i = 0; i < facts.receipt().logs().size(); i++) {
            var log = facts.receipt().logs().get(i);
            if (logIndex != null && logIndex.equals(log.logIndex()) && log.topics().size() > topicIndex) {
                return RuleEvidence.receipt(facts, logIndex, "/result/logs/" + i + "/topics/" + topicIndex);
            }
        }
        throw new IllegalStateException("风险地址证据缺少原始日志：logIndex=" + logIndex);
    }
}

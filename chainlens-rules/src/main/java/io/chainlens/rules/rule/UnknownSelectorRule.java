package io.chainlens.rules.rule;

import java.util.List;

import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.Rule;
import io.chainlens.rules.support.RuleEvidence;

/**
 * UNKNOWN-SELECTOR：calldata 无法唯一解析（UNRESOLVED 无候选 / AMBIGUOUS 多候选）。
 * confidence=0.8（信息缺口，本身不必然是风险），severity=LOW。
 * 候选列表如实写入描述，不武断选一（设计文档 5.3 / 6.2）。
 */
public final class UnknownSelectorRule implements Rule {

    public static final String RULE_ID = "UNKNOWN-SELECTOR";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public List<Finding> evaluate(TxFacts facts) {
        CalldataAnalysis calldata = facts.calldata();
        if (calldata == null || !calldata.hasInput()) {
            return List.of(); // 本就没有 calldata
        }
        if (calldata.status() == CalldataAnalysis.Status.RESOLVED) {
            return List.of();
        }
        String candidatesText = calldata.candidates().isEmpty()
                ? "无任何已知候选"
                : "存在多个候选：" + String.join("、", calldata.candidates());
        return List.of(new Finding(
                RULE_ID,
                Severity.LOW,
                0.8,
                "存在无法唯一识别的函数调用",
                "calldata 的函数签名无法唯一解析（" + candidatesText + "）。"
                        + "4-byte selector 存在碰撞，仅凭 selector 不能确定函数。"
                        + "若目标是知名协议，请核对合约地址与 ABI 来源。",
                RuleEvidence.transaction(facts, "/result/input")
        ));
    }
}

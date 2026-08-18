package io.chainlens.rules.rule;

import java.util.List;

import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.Rule;
import io.chainlens.rules.support.RuleEvidence;

/**
 * MANY-TOKEN-TRANSFERS：单笔交易 Transfer 事件数超阈值（设计文档 6.2）。
 * <ul>
 *   <li>count &gt; highThreshold → HIGH，confidence 0.9；</li>
 *   <li>count &gt; mediumThreshold → MEDIUM，confidence 0.8；</li>
 * </ul>
 * 阈值可配置（默认 20/100），启发式规则：批量转移常出现在 rug/资金拆分场景。
 */
public final class ManyTokenTransfersRule implements Rule {

    public static final String RULE_ID = "MANY-TOKEN-TRANSFERS";

    public static final int DEFAULT_MEDIUM_THRESHOLD = 20;
    public static final int DEFAULT_HIGH_THRESHOLD = 100;

    private final int mediumThreshold;
    private final int highThreshold;

    public ManyTokenTransfersRule() {
        this(DEFAULT_MEDIUM_THRESHOLD, DEFAULT_HIGH_THRESHOLD);
    }

    public ManyTokenTransfersRule(int mediumThreshold, int highThreshold) {
        if (mediumThreshold <= 0 || highThreshold <= mediumThreshold) {
            throw new IllegalArgumentException("阈值非法: 需满足 0 < mediumThreshold < highThreshold");
        }
        this.mediumThreshold = mediumThreshold;
        this.highThreshold = highThreshold;
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public List<Finding> evaluate(TxFacts facts) {
        int count = facts.transfers().size();
        if (count > highThreshold) {
            return List.of(finding(facts, count, Severity.HIGH, 0.9,
                    "该交易产生 " + count + " 次 ERC-20 Transfer（> " + highThreshold + "），"
                            + "大量转移常出现在批量分发、rug 或资金拆分场景，请结合交易目的判断。"));
        }
        if (count > mediumThreshold) {
            return List.of(finding(facts, count, Severity.MEDIUM, 0.8,
                    "该交易产生 " + count + " 次 ERC-20 Transfer（> " + mediumThreshold + "），"
                            + "请留意是否为批量分发或资金拆分。"));
        }
        return List.of();
    }

    private Finding finding(TxFacts facts, int count, Severity severity, double confidence, String description) {
        return new Finding(
                RULE_ID,
                severity,
                confidence,
                "单笔交易包含大量 Token 转移",
                description,
                RuleEvidence.receipt(facts, "/result/logs")
        );
    }
}

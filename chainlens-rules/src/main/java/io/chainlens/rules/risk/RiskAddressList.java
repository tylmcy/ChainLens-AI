package io.chainlens.rules.risk;

import java.util.Optional;

/**
 * 风险地址列表（设计文档 6.2 RISK-ADDRESS-HIT）：
 * 情报类数据源——标签属于第三方情报而非链上事实，confidence 永远 &lt; 1，
 * 命中时必须写明列表来源与条目标签。
 */
public interface RiskAddressList {

    /** 列表条目：地址（EIP-55 大小写不敏感）、标签、来源。 */
    record Entry(String address, String label, String source) {
    }

    /** 按归一化地址查找（实现负责大小写归一化），命中返回条目。 */
    Optional<Entry> find(String normalizedAddress);

    /** 列表来源标识（用于证据展示，如 "chainlens-builtin-sample"）。 */
    String source();
}

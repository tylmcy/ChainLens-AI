package io.chainlens.model;

/**
 * 规则命中结果（设计文档 6.1：每条命中必带 ruleId / severity / confidence / evidence / description）。
 *
 * <p>机器字段（ruleId/severity/confidence/evidence）由规则引擎定案，LLM 无权改动；
 * LLM 只能润色措辞并保持 ruleId 绑定（设计文档 6.3）。
 *
 * @param confidence 表达"判定依据的性质"而非模型自信度：确定性判定 1.0，
 *                   启发式/情报类 0.8~0.9（设计文档 6.2）
 */
public record Finding(
        String ruleId,
        Severity severity,
        double confidence,
        String title,
        String description,
        Evidence evidence
) {
}

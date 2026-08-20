package io.chainlens.model;

import java.util.List;

/**
 * 最终分析报告（设计文档附录的目标结构）。
 *
 * <p>约束（设计文档 9.5）：{@code limitations} 强制首条为免责声明
 * （"不构成投资建议，也不是完整的安全审计"），模板报告与 LLM 报告都必须包含。
 */
public record AnalysisReport(
        String summary,
        AnalysisStatus status,
        List<String> actions,
        List<Finding> findings,
        List<Source> sources,
        List<String> limitations
) {

    /** 报告依据来源：CHAIN = 链上数据定位；KNOWLEDGE = 知识库出处（M7 启用）。 */
    public record Source(String type, String label, String reference) {
    }
}

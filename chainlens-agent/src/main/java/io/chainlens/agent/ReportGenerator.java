package io.chainlens.agent;

import java.util.List;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;

/**
 * 报告生成器。实现分两种（设计文档 3.3 / 4.4）：
 * <ul>
 *   <li>{@link TemplateReportGenerator}：确定性模板（降级路径，llmUsed=false，M3）；</li>
 *   <li>Agent 报告生成器：LLM 组织措辞 + Schema/事实一致性校验（M5）。</li>
 * </ul>
 */
public interface ReportGenerator {

    AnalysisReport generate(TxFacts facts, List<Finding> findings);
}

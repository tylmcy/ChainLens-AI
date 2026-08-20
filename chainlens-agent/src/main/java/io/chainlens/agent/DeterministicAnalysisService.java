package io.chainlens.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.chainlens.chain.TxFactsProvider;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;

/**
 * 确定性分析服务（设计文档 3.1 阶段 1~5 的 M3 版本）：
 * 取证 → 解析 → 规则引擎 → 模板报告。全程无 LLM，是降级路径的本体。
 *
 * <p>链路任一环节抛出 {@code RpcException} 时直接上抛（由 api 层映射 404/503），
 * 本服务不吞错、不伪造（设计文档 9.1）。
 */
public final class DeterministicAnalysisService implements AnalysisService {

    private final TxFactsProvider factsProvider;
    private final RuleEngine ruleEngine;
    private final ReportGenerator reportGenerator;

    public DeterministicAnalysisService(TxFactsProvider factsProvider,
                                        RuleEngine ruleEngine,
                                        ReportGenerator reportGenerator) {
        this.factsProvider = factsProvider;
        this.ruleEngine = ruleEngine;
        this.reportGenerator = reportGenerator;
    }

    @Override
    public AnalysisResult analyze(String txHash, AnalysisProgressListener listener) {
        long start = System.currentTimeMillis();
        try {
            listener.onStage("forensics", "开始链上取证与解析（交易/回执/区块/代码）");
            TxFacts facts = factsProvider.assemble(txHash);

            listener.onStage("rules", "执行确定性风险规则");
            List<Finding> findings = ruleEngine.evaluate(facts);
            listener.onRulesCompleted(findings);

            listener.onStage("report", "生成结构化报告");
            AnalysisReport report = reportGenerator.generate(facts, findings);
            listener.onReportCompleted(report);

            AnalysisResult result = new AnalysisResult(
                    UUID.randomUUID().toString(),
                    txHash,
                    facts.network(),
                    report,
                    facts,
                    findings,
                    List.of(),
                    false,   // llmUsed
                    true,    // degraded（未使用 LLM，按设计文档 3.3 明确标记降级）
                    false,   // persisted（由 api 层落库后置 true）
                    System.currentTimeMillis() - start,
                    Instant.now()
            );
            listener.onAnalysisCompleted(result);
            return result;
        } catch (RuntimeException e) {
            listener.onAnalysisFailed(e);
            throw e;
        }
    }
}

package io.chainlens.mcp;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.Network;
import io.chainlens.model.ToolCallStep;

/** MCP 完整分析输出：与领域结果等价，但为协议 schema 明确标注可空链上字段。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpAnalysisResultView(
        String analysisId,
        String txHash,
        Network network,
        ReportView report,
        McpTxFactsView facts,
        List<McpFindingView> findings,
        List<ToolCallStep> toolSteps,
        boolean llmUsed,
        boolean degraded,
        boolean persisted,
        long totalDurationMs,
        Instant createdAt
) {

    public static McpAnalysisResultView from(AnalysisResult result) {
        return new McpAnalysisResultView(result.analysisId(), result.txHash(), result.network(),
                ReportView.from(result.report()), McpTxFactsView.from(result.facts()),
                result.findings().stream().map(McpFindingView::from).toList(),
                List.copyOf(result.toolSteps()), result.llmUsed(), result.degraded(), false,
                result.totalDurationMs(), result.createdAt());
    }

    public record ReportView(
            String summary,
            AnalysisStatus status,
            List<String> actions,
            List<McpFindingView> findings,
            List<SourceView> sources,
            List<String> limitations
    ) {
        static ReportView from(AnalysisReport report) {
            return new ReportView(report.summary(), report.status(), List.copyOf(report.actions()),
                    report.findings().stream().map(McpFindingView::from).toList(),
                    report.sources().stream().map(SourceView::from).toList(),
                    List.copyOf(report.limitations()));
        }
    }

    public record SourceView(String type, String label, String reference) {
        static SourceView from(AnalysisReport.Source source) {
            return new SourceView(source.type(), source.label(), source.reference());
        }
    }
}

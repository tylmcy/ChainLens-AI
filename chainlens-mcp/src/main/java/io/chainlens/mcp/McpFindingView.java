package io.chainlens.mcp;

import java.math.BigInteger;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.chainlens.model.Evidence;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;

/** MCP 专用 finding 视图：保留机器结论和原始 JSON 定位，并正确表达可空 logIndex。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpFindingView(
        String ruleId,
        Severity severity,
        double confidence,
        String title,
        String description,
        EvidenceView evidence
) {

    public static McpFindingView from(Finding finding) {
        return new McpFindingView(finding.ruleId(), finding.severity(), finding.confidence(),
                finding.title(), finding.description(), EvidenceView.from(finding.evidence()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvidenceView(
            BigInteger chainId,
            String network,
            BigInteger blockNumber,
            String transactionHash,
            @Nullable BigInteger logIndex,
            String rpcMethod,
            Instant fetchedAt,
            RawJsonRefView rawJsonRef
    ) {
        static EvidenceView from(Evidence evidence) {
            return new EvidenceView(evidence.chainId(), evidence.network(), evidence.blockNumber(),
                    evidence.transactionHash(), evidence.logIndex(), evidence.rpcMethod(),
                    evidence.fetchedAt(), new RawJsonRefView(
                            evidence.rawJsonRef().rpcMethod(), evidence.rawJsonRef().jsonPointer()));
        }
    }

    public record RawJsonRefView(String rpcMethod, String jsonPointer) {
    }
}

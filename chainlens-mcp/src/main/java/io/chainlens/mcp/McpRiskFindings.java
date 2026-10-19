package io.chainlens.mcp;

import java.util.List;

import io.chainlens.model.Network;

/** 指定交易的确定性规则结果；不包含任何 LLM 生成字段。 */
public record McpRiskFindings(String transactionHash, Network network, List<McpFindingView> findings) {

    public McpRiskFindings {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}

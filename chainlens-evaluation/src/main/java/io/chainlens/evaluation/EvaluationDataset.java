package io.chainlens.evaluation;

import java.util.List;

/**
 * 版本化交易评测集。ground truth 与原始 JSON-RPC 响应分开保存，避免修改预期时改写证据。
 * 所有金额均为十进制 wei 字符串，避免 JSON 数字精度损失。
 */
public record EvaluationDataset(String suiteId, String version, List<EvaluationCase> cases) {

    /** 单笔交易的来源、冻结响应目录和人工核对后的预期结果。 */
    public record EvaluationCase(
            String caseId,
            SourceType sourceType,
            String network,
            long chainId,
            String txHash,
            Source source,
            String fixtureDir,
            GroundTruth groundTruth
    ) {
    }

    public enum SourceType { ANVIL, HISTORICAL }

    /** 历史交易的 URI 可指向公共浏览器或公开 RPC；凭据不得写在这里。 */
    public record Source(String label, String uri, String capturedAt, String license) {
    }

    /**
     * 每条规则默认预期不命中；expectedFindings 只列应命中的 finding。
     * status 使用 SUCCESS/FAILED；事件列表必须显式存在，空数组代表无事件。
     */
    public record GroundTruth(
            ExpectedValue status,
            ExpectedValue valueWei,
            ExpectedValue gasCostWei,
            ExpectedValue fromNetWei,
            ExpectedValue toNetWei,
            CalldataExpectation calldata,
            List<TransferExpectation> transfers,
            List<ApprovalExpectation> approvals,
            List<ExpectedFinding> expectedFindings
    ) {
    }

    /** 不适用字段须显式标记；适用但值缺失应计为错误。 */
    public record ExpectedValue(boolean applicable, String value, String rawJsonRef) {
    }

    /** calldata 三态与解析出的选择器、函数签名由生产解析器生成。 */
    public record CalldataExpectation(
            boolean applicable,
            String status,
            String selector,
            String resolvedSignature,
            String rawJsonRef
    ) {
    }

    public record TransferExpectation(
            String tokenAddress,
            String from,
            String to,
            String valueWei,
            String logIndex,
            String rawJsonRef
    ) {
    }

    public record ApprovalExpectation(
            String tokenAddress,
            String owner,
            String spender,
            String valueWei,
            String logIndex,
            String rawJsonRef
    ) {
    }

    /** 预期命中必须提供 ruleId、置信度和能回到 RPC 响应的证据位置。 */
    public record ExpectedFinding(
            String ruleId,
            String severity,
            double confidence,
            String rpcMethod,
            String jsonPointer,
            String logIndex
    ) {
    }
}

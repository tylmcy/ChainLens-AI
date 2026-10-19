package io.chainlens.evaluation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.chainlens.evaluation.EvaluationDataset.ApprovalExpectation;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationDataset.ExpectedFinding;
import io.chainlens.evaluation.EvaluationDataset.ExpectedValue;
import io.chainlens.evaluation.EvaluationDataset.GroundTruth;
import io.chainlens.evaluation.EvaluationDataset.TransferExpectation;
import io.chainlens.model.Network;

/** 数据集入口：先整体预检，再允许任何分析，避免半套数据得出看似通过的分数。 */
public final class EvaluationDatasetLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final Set<String> RULE_IDS = Set.of(
            "ERC20-UNLIMITED-APPROVAL", "TX-FAILED", "TRANSFER-TO-ZERO-ADDRESS",
            "INTERACTION-WITH-EOA", "UNKNOWN-SELECTOR", "MANY-TOKEN-TRANSFERS",
            "RISK-ADDRESS-HIT");

    private EvaluationDatasetLoader() {
    }

    /** 加载完整 V1 基准集，并预检来源比例、ground truth 和每份 RPC 响应。 */
    public static EvaluationDataset load(Path manifest, Path fixtureRoot) {
        try {
            EvaluationDataset dataset = MAPPER.readValue(manifest.toFile(), EvaluationDataset.class);
            validate(dataset, fixtureRoot);
            return dataset;
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取评测数据集 " + manifest + ": " + e.getMessage(), e);
        }
    }

    static void validate(EvaluationDataset dataset, Path fixtureRoot) {
        if (dataset == null || !"transaction-eval".equals(dataset.suiteId())
                || !"v1".equals(dataset.version()) || dataset.cases() == null
                || dataset.cases().size() != 30) {
            throw new IllegalArgumentException("transaction-eval-v1 必须包含恰好 30 条用例");
        }
        Set<String> ids = new HashSet<>();
        int anvil = 0;
        int historical = 0;
        for (EvaluationCase item : dataset.cases()) {
            validateCase(item, fixtureRoot);
            if (!ids.add(item.caseId())) {
                throw new IllegalArgumentException(item.caseId() + " caseId 重复");
            }
            switch (item.sourceType()) {
                case ANVIL -> anvil++;
                case HISTORICAL -> historical++;
            }
        }
        if (anvil != 15 || historical != 15) {
            throw new IllegalArgumentException("数据集来源比例错误：ANVIL=" + anvil
                    + " HISTORICAL=" + historical + "，要求各 15 条");
        }
    }

    /** 包级入口用于针对一条用例验证边界；正式加载仍强制完整 30 条。 */
    static void validateCase(EvaluationCase item, Path fixtureRoot) {
        String id = item == null || item.caseId() == null ? "<missing-caseId>" : item.caseId();
        if (item == null || !id.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException(id + " caseId 格式错误");
        }
        if (item.sourceType() == null || item.network() == null || item.chainId() <= 0
                || item.txHash() == null || !item.txHash().matches("0x[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(id + " 来源、网络、chainId 或交易哈希无效");
        }
        if (!Network.fromChainId(item.chainId()).label().equals(item.network())) {
            throw new IllegalArgumentException(id + " network 与 chainId 不一致");
        }
        if ((item.sourceType() == EvaluationDataset.SourceType.ANVIL && item.chainId() != 31337)
                || (item.sourceType() == EvaluationDataset.SourceType.HISTORICAL && item.chainId() != 1)) {
            throw new IllegalArgumentException(id + " 来源类别与链 ID 不一致");
        }
        if (item.source() == null || blank(item.source().label()) || blank(item.source().uri())
                || blank(item.source().license()) || blank(item.source().capturedAt())) {
            throw new IllegalArgumentException(id + " 缺少来源或许可元数据");
        }
        try {
            LocalDate.parse(item.source().capturedAt());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(id + " capturedAt 必须为 ISO 日期", e);
        }
        if (!id.equals(item.fixtureDir())) {
            throw new IllegalArgumentException(id + " fixtureDir 必须与 caseId 一致");
        }
        validateGroundTruth(id, item.groundTruth());
        Path root = fixtureRoot.toAbsolutePath().normalize();
        Path directory = root.resolve(item.fixtureDir()).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException(id + " fixture 路径越界");
        }
        FixtureEthRpcClient rpc = new FixtureEthRpcClient(id, item.txHash(), directory);
        if (rpc.chainId() != item.chainId()) {
            throw new IllegalArgumentException(id + " fixture chainId 与清单不一致");
        }
    }

    private static void validateGroundTruth(String id, GroundTruth truth) {
        if (truth == null || truth.transfers() == null || truth.approvals() == null
                || truth.expectedFindings() == null || truth.calldata() == null) {
            throw new IllegalArgumentException(id + " groundTruth 缺少必填列表或 calldata");
        }
        validateValue(id, "status", truth.status(), false);
        validateValue(id, "valueWei", truth.valueWei(), true);
        validateValue(id, "gasCostWei", truth.gasCostWei(), true);
        validateValue(id, "fromNetWei", truth.fromNetWei(), true);
        validateValue(id, "toNetWei", truth.toNetWei(), true);
        if (truth.calldata().status() == null || truth.calldata().rawJsonRef() == null
                || !truth.calldata().rawJsonRef().startsWith("/result/")) {
            throw new IllegalArgumentException(id + " calldata 缺少三态或原始位置");
        }
        if (!List.of("RESOLVED", "AMBIGUOUS", "UNRESOLVED").contains(truth.calldata().status())) {
            throw new IllegalArgumentException(id + " calldata 三态非法");
        }
        for (TransferExpectation event : truth.transfers()) {
            if (event == null || !pointer(event.rawJsonRef()) || blank(event.logIndex())
                    || blank(event.tokenAddress()) || blank(event.from()) || blank(event.to())
                    || blank(event.valueWei())) {
                throw new IllegalArgumentException(id + " Transfer groundTruth 不完整");
            }
        }
        for (ApprovalExpectation event : truth.approvals()) {
            if (event == null || !pointer(event.rawJsonRef()) || blank(event.logIndex())
                    || blank(event.tokenAddress()) || blank(event.owner()) || blank(event.spender())
                    || blank(event.valueWei())) {
                throw new IllegalArgumentException(id + " Approval groundTruth 不完整");
            }
        }
        for (ExpectedFinding finding : truth.expectedFindings()) {
            if (finding == null || !RULE_IDS.contains(finding.ruleId()) || blank(finding.severity())
                    || finding.confidence() < 0 || finding.confidence() > 1
                    || blank(finding.rpcMethod()) || !pointer(finding.jsonPointer())) {
                throw new IllegalArgumentException(id + " finding groundTruth 缺少 ruleId、confidence 或 evidence 位置");
            }
        }
    }

    private static void validateValue(String id, String name, ExpectedValue field, boolean integer) {
        if (field == null || (field.applicable() && (blank(field.value()) || !pointer(field.rawJsonRef())))) {
            throw new IllegalArgumentException(id + " groundTruth." + name + " 缺少适用值或原始位置");
        }
        if (field.applicable() && integer && !field.value().matches("-?[0-9]+")) {
            throw new IllegalArgumentException(id + " groundTruth." + name + " 必须为十进制 wei 字符串");
        }
        if (field.applicable() && !integer && !List.of("SUCCESS", "FAILED").contains(field.value())) {
            throw new IllegalArgumentException(id + " groundTruth.status 非法");
        }
    }

    private static boolean pointer(String value) {
        return value != null && (value.equals("/result") || value.startsWith("/result/"));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

package io.chainlens.agent;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;

import static org.assertj.core.api.Assertions.assertThat;

class FactConsistencyValidatorTest {

    private static final String DISCLAIMER = TemplateReportGenerator.DISCLAIMER;

    private static AnalysisReport report(String summary, List<Finding> findings, List<String> limitations) {
        return new AnalysisReport(summary, AnalysisStatus.SUCCESS, List.of(), findings, List.of(), limitations);
    }

    private static List<Finding> rulesFindings() {
        return List.of(new Finding("ERC20-UNLIMITED-APPROVAL", Severity.HIGH, 1.0,
                "检测到无限额度授权", "desc", null));
    }

    @Test
    @DisplayName("合法报告（无哈希/无长数字/规则一致/免责声明首条）→ 零违规")
    void validReportPasses() {
        var facts = AgTxFactsFactory.create().build();
        var report = report("该交易成功完成。", rulesFindings(), List.of(DISCLAIMER, "V1 不解析 revert 原因。"));

        assertThat(FactConsistencyValidator.validate(report, facts, rulesFindings())).isEmpty();
    }

    @Test
    @DisplayName("findings.ruleId 与规则引擎不一致 → 违规")
    void ruleIdMismatchDetected() {
        var facts = AgTxFactsFactory.create().build();
        var report = report("该交易成功完成。",
                List.of(new Finding("TX-FAILED", Severity.MEDIUM, 1.0, "交易失败", "desc", null)),
                List.of(DISCLAIMER));

        assertThat(FactConsistencyValidator.validate(report, facts, rulesFindings()))
                .anyMatch(v -> v.contains("ruleId"));
    }

    @Test
    @DisplayName("文本中出现证据之外的哈希 → 违规（防 LLM 编地址）")
    void unknownHashDetected() {
        var facts = AgTxFactsFactory.create().build();
        String fakeHash = "0x" + "d".repeat(64);
        var report = report("向 " + fakeHash + " 转账。", rulesFindings(), List.of(DISCLAIMER));

        assertThat(FactConsistencyValidator.validate(report, facts, rulesFindings()))
                .anyMatch(v -> v.contains("哈希"));
    }

    @Test
    @DisplayName("文本中出现证据之外的长数字 → 违规（防 LLM 改金额）")
    void unknownLongNumberDetected() {
        var facts = AgTxFactsFactory.create().build();
        var report = report("转出 99999999999999 wei。", rulesFindings(), List.of(DISCLAIMER));

        assertThat(FactConsistencyValidator.validate(report, facts, rulesFindings()))
                .anyMatch(v -> v.contains("长数字"));
    }

    @Test
    @DisplayName("真实存在于证据中的哈希与数字 → 不违规")
    void knownHashAndNumberPass() {
        var facts = AgTxFactsFactory.create()
                .withApprovals(List.of(new ApprovalEvent(AgTxFactsFactory.TOKEN, AgTxFactsFactory.FROM,
                        "0x4444444444444444444444444444444444444444",
                        AgTxFactsFactory.TWO_256_MINUS_1, BigInteger.ONE)))
                .build();
        // 2^256-1 的十进制 = 115792089237316195423570985008687907853269984665640564039457584007913129639935（长数字，在允许池中）
        var report = report("向 " + AgTxFactsFactory.FROM + " 授权 "
                        + AgTxFactsFactory.TWO_256_MINUS_1 + " wei。",
                rulesFindings(), List.of(DISCLAIMER));

        assertThat(FactConsistencyValidator.validate(report, facts, rulesFindings())).isEmpty();
    }

    @Test
    @DisplayName("区块时间戳来自案卷 → 不误报为证据外长数字")
    void blockTimestampPasses() {
        var facts = AgTxFactsFactory.create().build();
        var report = report("区块时间戳为 1700000000。", rulesFindings(), List.of(DISCLAIMER));

        assertThat(FactConsistencyValidator.validate(report, facts, rulesFindings())).isEmpty();
    }

    @Test
    @DisplayName("limitations 首条不是免责声明 → 违规（设计文档 9.5 硬约束）")
    void missingDisclaimerDetected() {
        var facts = AgTxFactsFactory.create().build();
        var report = report("该交易成功完成。", rulesFindings(), List.of("其他说明"));

        assertThat(FactConsistencyValidator.validate(report, facts, rulesFindings()))
                .anyMatch(v -> v.contains("免责声明"));
    }
}

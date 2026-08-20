package io.chainlens.agent;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.rule.UnlimitedApprovalRule;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateReportGeneratorTest {

    private final TemplateReportGenerator generator = new TemplateReportGenerator();

    @Test
    @DisplayName("limitations 首条必须是免责声明（设计文档 9.5 硬约束）")
    void disclaimerIsFirstLimitation() {
        var report = generator.generate(AgTxFactsFactory.create().build(), List.of());

        assertThat(report.limitations()).isNotEmpty();
        assertThat(report.limitations().get(0)).isEqualTo(TemplateReportGenerator.DISCLAIMER);
        assertThat(report.limitations().get(0)).contains("不构成投资建议", "不是完整的安全审计");
    }

    @Test
    @DisplayName("降级报告标注未使用 LLM；status 跟随回执")
    void degradedMarkersAndStatus() {
        var failed = generator.generate(AgTxFactsFactory.create().withStatus("0x0").build(), List.of());
        var success = generator.generate(AgTxFactsFactory.create().build(), List.of());

        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(success.status()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(failed.limitations()).anyMatch(l -> l.contains("未使用 LLM"));
    }

    @Test
    @DisplayName("findings 原样呈现规则引擎结果（机器字段零改动，设计文档 6.3）")
    void findingsArePassthrough() {
        RuleEngine engine = new RuleEngine(List.of(new UnlimitedApprovalRule()));
        var facts = AgTxFactsFactory.create()
                .withApprovals(List.of(new ApprovalEvent(AgTxFactsFactory.TOKEN, AgTxFactsFactory.FROM,
                        "0x4444444444444444444444444444444444444444",
                        AgTxFactsFactory.TWO_256_MINUS_1, BigInteger.ONE)))
                .build();
        List<Finding> findings = engine.evaluate(facts);

        var report = generator.generate(facts, findings);

        assertThat(report.findings()).isEqualTo(findings);
        assertThat(report.findings().get(0).ruleId()).isEqualTo("ERC20-UNLIMITED-APPROVAL");
        assertThat(report.findings().get(0).severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("actions 由规则映射确定性生成（无限授权 → 撤销授权建议）")
    void actionsDerivedFromFindings() {
        var facts = AgTxFactsFactory.create()
                .withApprovals(List.of(new ApprovalEvent(AgTxFactsFactory.TOKEN, AgTxFactsFactory.FROM,
                        "0x4444444444444444444444444444444444444444",
                        AgTxFactsFactory.TWO_256_MINUS_1, BigInteger.ONE)))
                .build();

        var report = generator.generate(facts, List.of(new Finding(
                "ERC20-UNLIMITED-APPROVAL", Severity.HIGH, 1.0, "检测到无限额度授权", "desc", null)));

        assertThat(report.actions()).anyMatch(a -> a.contains("撤销"));
    }

    @Test
    @DisplayName("无命中时 actions 为空；sources 恒为三处链上数据定位")
    void emptyActionsAndChainSources() {
        var report = generator.generate(AgTxFactsFactory.create().build(), List.of());

        assertThat(report.actions()).isEmpty();
        assertThat(report.sources()).extracting(s -> s.type()).containsOnly("CHAIN");
        assertThat(report.sources()).hasSize(3);
    }

    @Test
    @DisplayName("未知 selector 与代码不可查如实进入 limitations")
    void limitationsReflectUnknowns() {
        var facts = AgTxFactsFactory.create()
                .withCalldata(new CalldataAnalysis(true, "0xdeadbeef", List.of(), null,
                        CalldataAnalysis.Status.UNRESOLVED))
                .build();

        var report = generator.generate(facts, List.of());

        assertThat(report.limitations()).anyMatch(l -> l.contains("无法唯一解析"));
        assertThat(report.limitations()).anyMatch(l -> l.contains("历史合约代码不可查"));
    }
}

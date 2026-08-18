package io.chainlens.rules;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.rules.risk.BuiltInRiskAddressList;
import io.chainlens.rules.rule.FailedTransactionRule;
import io.chainlens.rules.rule.InteractionWithEoaRule;
import io.chainlens.rules.rule.ManyTokenTransfersRule;
import io.chainlens.rules.rule.RiskAddressHitRule;
import io.chainlens.rules.rule.TransferToZeroAddressRule;
import io.chainlens.rules.rule.UnknownSelectorRule;
import io.chainlens.rules.rule.UnlimitedApprovalRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 演示证据：构造一笔"无限额度授权 + 交易失败"的案卷，
 * 全引擎执行并把 findings 导出到 target/m2-demo-findings.json 供人工查验。
 */
class RuleEngineDemoTest {

    @Test
    @DisplayName("M2 演示：案卷 → 全引擎 findings（导出 JSON 证据）")
    void demoFindingsExport() throws IOException {
        RuleEngine engine = new RuleEngine(List.of(
                new UnlimitedApprovalRule(),
                new FailedTransactionRule(),
                new TransferToZeroAddressRule(),
                new InteractionWithEoaRule(),
                new UnknownSelectorRule(),
                new ManyTokenTransfersRule(),
                new RiskAddressHitRule(List.of(BuiltInRiskAddressList.fromClasspath(BuiltInRiskAddressList.DEFAULT_RESOURCE)))
        ));

        var facts = TxFactsFactory.create()
                .withStatus("0x0")
                .withApprovals(List.of(new ApprovalEvent(TxFactsFactory.TOKEN, TxFactsFactory.FROM,
                        "0x4444444444444444444444444444444444444444",
                        TxFactsFactory.TWO_256_MINUS_1, BigInteger.ONE)))
                .build();

        var findings = engine.evaluate(facts);

        // 精确断言：恰好命中 2 条（无限额度授权 + 交易失败）
        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(f -> f.ruleId())
                .containsExactlyInAnyOrder("ERC20-UNLIMITED-APPROVAL", "TX-FAILED");

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Files.writeString(Path.of("target/m2-demo-findings.json"), mapper.writeValueAsString(findings));
    }
}

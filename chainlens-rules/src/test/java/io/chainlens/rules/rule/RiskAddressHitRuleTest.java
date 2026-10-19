package io.chainlens.rules.rule;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.EthLog;
import io.chainlens.model.Severity;
import io.chainlens.model.TransferEvent;
import io.chainlens.rules.TxFactsFactory;
import io.chainlens.rules.risk.BuiltInRiskAddressList;
import io.chainlens.rules.risk.RiskAddressList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskAddressHitRuleTest {

    private static final String SPENDER = "0x4444444444444444444444444444444444444444";

    private static BuiltInRiskAddressList listOf(String address, String label) {
        BuiltInRiskAddressList list = new BuiltInRiskAddressList("test-list");
        list.add(address, label, "test-source");
        return list;
    }

    /** 构造真实回执日志位置；区块 logIndex 故意与回执数组下标不同。 */
    private static EthLog eventLog(BigInteger logIndex, String first, String second) {
        return new EthLog(TxFactsFactory.TOKEN,
                List.of("0x" + "00".repeat(32), topic(first), topic(second)), "0x", logIndex, false);
    }

    private static String topic(String address) {
        return "0x" + "0".repeat(24) + address.substring(2);
    }

    @Test
    @DisplayName("正例：tx.from 命中 → HIGH / 0.85，描述写明来源与标签")
    void fromHitTriggers() {
        var rule = new RiskAddressHitRule(List.of(listOf(TxFactsFactory.FROM, "钓鱼地址")));

        var findings = rule.evaluate(TxFactsFactory.create().build());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("RISK-ADDRESS-HIT");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(findings.get(0).confidence()).isEqualTo(0.85);
        assertThat(findings.get(0).description()).contains("test-source", "钓鱼地址");
        assertThat(findings.get(0).evidence().rawJsonRef().jsonPointer()).isEqualTo("/result/from");
    }

    @Test
    @DisplayName("tx.to 命中时证据不能错误指向 tx.from")
    void toHitPointsToRecipient() {
        var rule = new RiskAddressHitRule(List.of(listOf(TxFactsFactory.TO, "风险收款方")));

        var finding = rule.evaluate(TxFactsFactory.create().build()).get(0);

        assertThat(finding.evidence().rawJsonRef().rpcMethod()).isEqualTo("eth_getTransactionByHash");
        assertThat(finding.evidence().rawJsonRef().jsonPointer()).isEqualTo("/result/to");
    }

    @Test
    @DisplayName("正例：Transfer.to 命中（事件参与方）")
    void transferToHitTriggers() {
        var rule = new RiskAddressHitRule(List.of(listOf("0x2222222222222222222222222222222222222222", "风险路由")));
        BigInteger logIndex = BigInteger.valueOf(7);
        var facts = TxFactsFactory.create()
                .withTransfers(List.of(new TransferEvent(TxFactsFactory.TOKEN, TxFactsFactory.FROM,
                        "0x2222222222222222222222222222222222222222", BigInteger.ONE, logIndex)))
                .withLogs(List.of(eventLog(logIndex, TxFactsFactory.FROM,
                        "0x2222222222222222222222222222222222222222")))
                .build();

        var finding = rule.evaluate(facts).get(0);
        assertThat(finding.evidence().logIndex()).isEqualTo(logIndex);
        assertThat(finding.evidence().rawJsonRef().jsonPointer()).isEqualTo("/result/logs/0/topics/2");
    }

    @Test
    @DisplayName("正例：Approval.spender 命中（参与方精确定义覆盖）")
    void approvalSpenderHitTriggers() {
        var rule = new RiskAddressHitRule(List.of(listOf(SPENDER, "恶意授权方")));
        var facts = TxFactsFactory.create()
                .withApprovals(List.of(new ApprovalEvent(TxFactsFactory.TOKEN, TxFactsFactory.FROM,
                        SPENDER, BigInteger.ONE, BigInteger.ZERO)))
                .withLogs(List.of(eventLog(BigInteger.ZERO, TxFactsFactory.FROM, SPENDER)))
                .build();

        var finding = rule.evaluate(facts).get(0);
        assertThat(finding.evidence().rawJsonRef().jsonPointer()).isEqualTo("/result/logs/0/topics/2");
    }

    @Test
    @DisplayName("事件命中但缺少原始日志时失败，不伪造证据路径")
    void missingRawLogRejected() {
        var rule = new RiskAddressHitRule(List.of(listOf(SPENDER, "恶意授权方")));
        var facts = TxFactsFactory.create()
                .withApprovals(List.of(new ApprovalEvent(TxFactsFactory.TOKEN, TxFactsFactory.FROM,
                        SPENDER, BigInteger.ONE, BigInteger.ZERO)))
                .build();

        assertThatThrownBy(() -> rule.evaluate(facts))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少原始日志");
    }

    @Test
    @DisplayName("边界：只有 token 合约地址（log.address）在列表中 → 不触发（参与方不含 token 合约）")
    void tokenContractAloneDoesNotTrigger() {
        // token 合约地址与 tx.to 刻意不同，确保命中只可能来自"token 合约在列表中"这一条路径
        String distinctToken = "0x5555555555555555555555555555555555555555";
        var rule = new RiskAddressHitRule(List.of(listOf(distinctToken, "风险代币")));
        var facts = TxFactsFactory.create()
                .withTransfers(List.of(new TransferEvent(distinctToken, TxFactsFactory.FROM,
                        "0x2222222222222222222222222222222222222222", BigInteger.ONE, BigInteger.ZERO)))
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }

    @Test
    @DisplayName("大小写不敏感：列表存大写地址，参与者小写仍命中（EIP-55 归一化）")
    void caseInsensitiveMatching() {
        var rule = new RiskAddressHitRule(List.of(listOf("0x" + "AB".repeat(20), "大写地址")));
        var facts = TxFactsFactory.create()
                .withFrom("0x" + "ab".repeat(20))
                .build();

        assertThat(rule.evaluate(facts)).hasSize(1);
    }

    @Test
    @DisplayName("反例：未命中任何列表")
    void noHitDoesNotTrigger() {
        var rule = new RiskAddressHitRule(List.of(listOf("0x9999999999999999999999999999999999999999", "无关地址")));

        assertThat(rule.evaluate(TxFactsFactory.create().build())).isEmpty();
    }

    @Test
    @DisplayName("构造校验：空列表拒绝")
    void emptyListsRejected() {
        assertThatThrownBy(() -> new RiskAddressHitRule(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

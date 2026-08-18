package io.chainlens.chain.parse;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.chain.Hexes;
import io.chainlens.model.EthLog;

import static org.assertj.core.api.Assertions.assertThat;

class EventDecoderTest {

    private static final String TOKEN = "0x3333333333333333333333333333333333333333";
    private static final String FROM = "0x1111111111111111111111111111111111111111";
    private static final String TO = "0x2222222222222222222222222222222222222222";

    private final EventDecoder decoder = new EventDecoder();

    private static EthLog log(String address, List<String> topics, String data, long logIndex) {
        return new EthLog(address, topics, data, BigInteger.valueOf(logIndex), false);
    }

    @Test
    @DisplayName("解码标准 Transfer（indexed 地址 + uint256 data）")
    void decodesTransfer() {
        String valueHex = "0x" + "0".repeat(64 - "de0b6b3a7640000".length()) + "de0b6b3a7640000";
        EthLog log = log(TOKEN,
                List.of(EventDecoder.TRANSFER_TOPIC, Hexes.addressToTopic(FROM), Hexes.addressToTopic(TO)),
                valueHex, 0);

        EventDecoder.DecodedEvents events = decoder.decode(List.of(log));

        assertThat(events.transfers()).hasSize(1);
        var transfer = events.transfers().get(0);
        assertThat(transfer.tokenAddress()).isEqualTo(TOKEN);
        assertThat(transfer.from()).isEqualTo(FROM);
        assertThat(transfer.to()).isEqualTo(TO);
        assertThat(transfer.valueWei()).isEqualTo(new BigInteger("1000000000000000000"));
        assertThat(events.approvals()).isEmpty();
        assertThat(events.unknownTopics()).isEmpty();
    }

    @Test
    @DisplayName("解码 Approval（含 2^256-1 无限额度）")
    void decodesApproval() {
        EthLog log = log(TOKEN,
                List.of(EventDecoder.APPROVAL_TOPIC, Hexes.addressToTopic(FROM), Hexes.addressToTopic(TO)),
                "0x" + "f".repeat(64), 1);

        EventDecoder.DecodedEvents events = decoder.decode(List.of(log));

        assertThat(events.approvals()).hasSize(1);
        var approval = events.approvals().get(0);
        assertThat(approval.owner()).isEqualTo(FROM);
        assertThat(approval.spender()).isEqualTo(TO);
        assertThat(approval.valueWei()).isEqualTo(new BigInteger("2").pow(256).subtract(BigInteger.ONE));
    }

    @Test
    @DisplayName("未知 topic 如实列入 unknownTopics（不猜测）")
    void collectsUnknownTopics() {
        EthLog log = log(TOKEN,
                List.of("0xdeadbeef00000000000000000000000000000000000000000000000000000000"),
                "0x", 2);

        EventDecoder.DecodedEvents events = decoder.decode(List.of(log));

        assertThat(events.transfers()).isEmpty();
        assertThat(events.unknownTopics()).containsExactly("0xdeadbeef00000000000000000000000000000000000000000000000000000000");
    }

    @Test
    @DisplayName("topic 是 Transfer 但 data 布局异常 → 归入未解码，不产出半截事件")
    void malformedDataGoesToUnknown() {
        EthLog log = log(TOKEN,
                List.of(EventDecoder.TRANSFER_TOPIC, Hexes.addressToTopic(FROM), Hexes.addressToTopic(TO)),
                "0x1234", 3);

        EventDecoder.DecodedEvents events = decoder.decode(List.of(log));

        assertThat(events.transfers()).isEmpty();
        assertThat(events.unknownTopics()).containsExactly(EventDecoder.TRANSFER_TOPIC);
    }

    @Test
    @DisplayName("topic 数量不足（非标准事件）→ 未解码")
    void shortTopicsGoToUnknown() {
        EthLog log = log(TOKEN,
                List.of(EventDecoder.TRANSFER_TOPIC, Hexes.addressToTopic(FROM)),
                "0x", 4);

        EventDecoder.DecodedEvents events = decoder.decode(List.of(log));

        assertThat(events.transfers()).isEmpty();
        assertThat(events.unknownTopics()).containsExactly(EventDecoder.TRANSFER_TOPIC);
    }
}

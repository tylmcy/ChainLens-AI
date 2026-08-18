package io.chainlens.chain;

import java.math.BigInteger;

import io.chainlens.model.Hexes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HexesTest {

    @Test
    @DisplayName("数量型 hex → BigInteger/Long")
    void parsesQuantities() {
        assertThat(Hexes.hexToBigInteger("0x1a")).isEqualTo(BigInteger.valueOf(26));
        assertThat(Hexes.hexToBigInteger("0x")).isEqualTo(BigInteger.ZERO);
        assertThat(Hexes.hexToLong("0x10d4f")).isEqualTo(68943L);
    }

    @Test
    @DisplayName("数字 → 数量型 hex（无前导零，0 为 0x0）")
    void formatsQuantities() {
        assertThat(Hexes.toQuantity(26L)).isEqualTo("0x1a");
        assertThat(Hexes.toQuantity(0L)).isEqualTo("0x0");
        assertThat(Hexes.toQuantity(new BigInteger("68943"))).isEqualTo("0x10d4f");
    }

    @Test
    @DisplayName("地址归一化：补零、小写、0x 前缀")
    void normalizesAddresses() {
        assertThat(Hexes.normalizeAddress("1234"))
                .isEqualTo("0x" + "0".repeat(36) + "1234");
        assertThat(Hexes.normalizeAddress("0x" + "AB".repeat(20)).length()).isEqualTo(42);
        assertThat(Hexes.normalizeAddress("0x" + "AB".repeat(20)))
                .isEqualTo("0x" + "ab".repeat(20));
    }

    @Test
    @DisplayName("地址 → 32 字节 topic（24 字节零填充）")
    void addressToTopicLayout() {
        String address = "0x1234567890abcdef1234567890abcdef12345678";
        assertThat(Hexes.addressToTopic(address))
                .isEqualTo("0x" + "0".repeat(24) + "1234567890abcdef1234567890abcdef12345678");
    }

    @Test
    @DisplayName("非法输入抛异常（不静默吞错）")
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> Hexes.hexToBigInteger("0xzz"))
                .isInstanceOf(NumberFormatException.class);
    }
}

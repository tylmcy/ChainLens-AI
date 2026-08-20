package io.chainlens.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashExtractorTest {

    private static final String HASH = "0xabc0000000000000000000000000000000000000000000000000000000000001";

    @Test
    @DisplayName("从自然语言问句中提取第一个哈希")
    void extractsFromQuestion() {
        assertThat(HashExtractor.extract("帮我看看这笔交易 " + HASH + " 有没有风险"))
                .isEqualTo(HASH);
    }

    @Test
    @DisplayName("多哈希取第一个")
    void firstMatchWins() {
        String second = "0x" + "b".repeat(64);
        assertThat(HashExtractor.extract("先看 " + HASH + "，再看 " + second))
                .isEqualTo(HASH);
    }

    @Test
    @DisplayName("无哈希返回 null（纯正则，不调用 LLM）")
    void noHashReturnsNull() {
        assertThat(HashExtractor.extract("以太坊是什么")).isNull();
        assertThat(HashExtractor.extract("0x1234")).isNull();
        assertThat(HashExtractor.extract(null)).isNull();
    }
}

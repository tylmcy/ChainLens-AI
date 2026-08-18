package io.chainlens.chain.parse;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.CalldataAnalysis.Status;

import static org.assertj.core.api.Assertions.assertThat;

class CalldataDecoderTest {

    private final CalldataDecoder decoder = new CalldataDecoder(new CuratedSelectorRegistry());

    @Test
    @DisplayName("无 calldata（0x）→ UNRESOLVED 且 hasInput=false")
    void emptyInput() {
        CalldataAnalysis analysis = decoder.decode("0x");

        assertThat(analysis.hasInput()).isFalse();
        assertThat(analysis.status()).isEqualTo(Status.UNRESOLVED);
    }

    @Test
    @DisplayName("已知 selector → RESOLVED（transfer(address,uint256)）")
    void resolvesKnownSelector() {
        CalldataAnalysis analysis = decoder.decode(
                "0xa9059cbb" + "0".repeat(64));

        assertThat(analysis.status()).isEqualTo(Status.RESOLVED);
        assertThat(analysis.resolvedSignature()).isEqualTo("transfer(address,uint256)");
        assertThat(analysis.selector()).isEqualTo("0xa9059cbb");
    }

    @Test
    @DisplayName("大小写不敏感：0xA9059CBB 同样解析")
    void selectorIsCaseInsensitive() {
        CalldataAnalysis analysis = decoder.decode("0xA9059CBB" + "0".repeat(64));

        assertThat(analysis.status()).isEqualTo(Status.RESOLVED);
        assertThat(analysis.resolvedSignature()).isEqualTo("transfer(address,uint256)");
    }

    @Test
    @DisplayName("未知 selector → UNRESOLVED，候选为空")
    void unknownSelector() {
        CalldataAnalysis analysis = decoder.decode("0xdeadbeef" + "0".repeat(64));

        assertThat(analysis.status()).isEqualTo(Status.UNRESOLVED);
        assertThat(analysis.candidates()).isEmpty();
    }

    @Test
    @DisplayName("多候选（selector 碰撞）→ AMBIGUOUS，列出全部候选不武断选一")
    void ambiguousSelectorListsAllCandidates() {
        SelectorRegistry colliding = selector -> selector.equals("0x11111111")
                ? List.of("foo()", "bar(uint256)")
                : List.of();
        CalldataDecoder decoderWithCollision = new CalldataDecoder(colliding);

        CalldataAnalysis analysis = decoderWithCollision.decode("0x11111111" + "0".repeat(64));

        assertThat(analysis.status()).isEqualTo(Status.AMBIGUOUS);
        assertThat(analysis.candidates()).containsExactly("foo()", "bar(uint256)");
        assertThat(analysis.resolvedSignature()).isNull();
    }

    @Test
    @DisplayName("不足 4 字节的输入 → UNRESOLVED")
    void tooShortInput() {
        CalldataAnalysis analysis = decoder.decode("0x1234");

        assertThat(analysis.hasInput()).isTrue();
        assertThat(analysis.status()).isEqualTo(Status.UNRESOLVED);
    }
}

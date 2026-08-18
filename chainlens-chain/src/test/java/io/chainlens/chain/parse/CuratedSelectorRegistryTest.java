package io.chainlens.chain.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 精选 selector 表与公开公认值的回归校验：
 * 任何人在任何资料里查到的 transfer/approve/transferFrom selector 都是这三个值。
 */
class CuratedSelectorRegistryTest {

    private final CuratedSelectorRegistry registry = new CuratedSelectorRegistry();

    @Test
    @DisplayName("transfer(address,uint256) = 0xa9059cbb（公开公认值）")
    void transferSelector() {
        assertThat(registry.candidates("0xa9059cbb"))
                .containsExactly("transfer(address,uint256)");
    }

    @Test
    @DisplayName("approve(address,uint256) = 0x095ea7b3（公开公认值）")
    void approveSelector() {
        assertThat(registry.candidates("0x095ea7b3"))
                .containsExactly("approve(address,uint256)");
    }

    @Test
    @DisplayName("transferFrom(address,address,uint256) = 0x23b872dd（公开公认值）")
    void transferFromSelector() {
        assertThat(registry.candidates("0x23b872dd"))
                .containsExactly("transferFrom(address,address,uint256)");
    }

    @Test
    @DisplayName("未知 selector 返回空列表")
    void unknownSelector() {
        assertThat(registry.candidates("0xdeadbeef")).isEmpty();
        assertThat(registry.candidates(null)).isEmpty();
    }
}

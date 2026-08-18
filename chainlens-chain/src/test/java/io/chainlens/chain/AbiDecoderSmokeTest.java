package io.chainlens.chain;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0 技术风险探测（R3）：验证 web3j:abi 6.0.0 在 JDK 21 / macOS arm64 上可用，
 * 覆盖本项目实际需要的三个能力：event topic 指纹计算、indexed 地址解码、uint256 data 解码。
 *
 * <p>这些断言同时是该库 API 的"官方已知值"回归测试：
 * Transfer/Approval 的 topic0 是全网公认常量，任何版本回归都会被立刻发现。
 */
class AbiDecoderSmokeTest {

    private static final String ADDRESS_40_HEX = "1234567890abcdef1234567890abcdef12345678";

    @Test
    @DisplayName("Transfer(address,address,uint256) 的 topic0 与全网公认值一致")
    void computesTransferTopic0() {
        Event transfer = new Event("Transfer", List.of(
                new TypeReference<Address>(true) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Uint256>(false) {}));

        assertThat(EventEncoder.encode(transfer))
                .isEqualTo("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    }

    @Test
    @DisplayName("Approval(address,address,uint256) 的 topic0 与全网公认值一致")
    void computesApprovalTopic0() {
        Event approval = new Event("Approval", List.of(
                new TypeReference<Address>(true) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Uint256>(false) {}));

        assertThat(EventEncoder.encode(approval))
                .isEqualTo("0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925");
    }

    @Test
    @DisplayName("从 topic 解码 indexed address（高位补零）")
    void decodesIndexedAddressFromTopic() {
        // topics[1] 的布局：0x + 24 字节零填充 + 20 字节地址
        String topic = "0x" + "0".repeat(24) + ADDRESS_40_HEX;

        Type decoded = FunctionReturnDecoder.decodeIndexedValue(topic, new TypeReference<Address>() {});

        assertThat(decoded).isInstanceOf(Address.class);
        // 注：web3j 6 的 Address.getValue() 带 0x 前缀，与 4.x 不带前缀不同，实现时注意归一化
        assertThat(((Address) decoded).getValue()).isEqualTo("0x" + ADDRESS_40_HEX);
    }

    @Test
    @DisplayName("从 data 解码 uint256（大端 32 字节 word）")
    @SuppressWarnings("unchecked")
    void decodesUint256FromData() {
        // 1 ETH = 10^18 wei，hex 为 0x0de0b6b3a7640000
        String data = "0x" + "0".repeat(64 - "0de0b6b3a7640000".length()) + "0de0b6b3a7640000";

        // web3j 6 的 decode 签名要求 List<TypeReference<Type>>，
        // 其 Function 实现内部同样使用未检查强转，这里保持一致。
        List<Type> decoded = FunctionReturnDecoder.decode(data,
                List.of((TypeReference<Type>) (TypeReference) new TypeReference<Uint256>() {}));

        assertThat(decoded).hasSize(1);
        assertThat(((Uint256) decoded.get(0)).getValue())
                .isEqualTo(new BigInteger("1000000000000000000"));
    }
}

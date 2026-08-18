package io.chainlens.chain.parse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import io.chainlens.model.Hexes;
import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.EthLog;
import io.chainlens.model.TransferEvent;

/**
 * ERC-20 事件解码（V1 只解码 Transfer 与 Approval，设计文档 5.3）。
 *
 * <p>topic0 指纹来自签名 keccak256，是全网公认常量（在单测中与公开值校验）。
 * 解码失败的日志（参数布局异常等）归入 unknownTopics 如实标注，不猜测。
 */
public final class EventDecoder {

    public static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    public static final String APPROVAL_TOPIC = "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925";

    /** 解码结果：已知事件 + 未能解码的日志 topic 指纹（去重）。 */
    public record DecodedEvents(
            List<TransferEvent> transfers,
            List<ApprovalEvent> approvals,
            List<String> unknownTopics
    ) {
    }

    public DecodedEvents decode(List<EthLog> logs) {
        List<TransferEvent> transfers = new ArrayList<>();
        List<ApprovalEvent> approvals = new ArrayList<>();
        Set<String> unknownTopics = new LinkedHashSet<>();

        for (EthLog log : logs) {
            if (log.topics().isEmpty()) {
                unknownTopics.add("(无 topic)");
                continue;
            }
            String topic0 = log.topics().get(0).toLowerCase();
            try {
                if (TRANSFER_TOPIC.equals(topic0) && log.topics().size() == 3) {
                    transfers.add(new TransferEvent(
                            Hexes.normalizeAddress(log.address()),
                            decodeIndexedAddress(log.topics().get(1)),
                            decodeIndexedAddress(log.topics().get(2)),
                            decodeUint256(log.data()),
                            log.logIndex()));
                } else if (APPROVAL_TOPIC.equals(topic0) && log.topics().size() == 3) {
                    approvals.add(new ApprovalEvent(
                            Hexes.normalizeAddress(log.address()),
                            decodeIndexedAddress(log.topics().get(1)),
                            decodeIndexedAddress(log.topics().get(2)),
                            decodeUint256(log.data()),
                            log.logIndex()));
                } else {
                    unknownTopics.add(topic0);
                }
            } catch (RuntimeException e) {
                // 参数布局不符合标准 ERC-20 事件：如实归入未解码
                unknownTopics.add(topic0);
            }
        }
        return new DecodedEvents(transfers, approvals, List.copyOf(unknownTopics));
    }

    private static String decodeIndexedAddress(String topic) {
        Type decoded = FunctionReturnDecoder.decodeIndexedValue(topic, new TypeReference<Address>() {});
        // web3j 6 的 Address.getValue() 带 0x 前缀（M0 冒烟已确认），统一归一化
        return Hexes.normalizeAddress(((Address) decoded).getValue());
    }

    private static BigInteger decodeUint256(String data) {
        if (data == null || data.length() != 66) {
            throw new IllegalArgumentException("非标准 uint256 data 长度: " + (data == null ? "null" : data.length()));
        }
        List<Type> decoded = FunctionReturnDecoder.decode(data,
                List.of((TypeReference<Type>) (TypeReference) new TypeReference<Uint256>() {}));
        return ((Uint256) decoded.get(0)).getValue();
    }
}

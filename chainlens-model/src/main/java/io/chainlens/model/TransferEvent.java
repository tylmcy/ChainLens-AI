package io.chainlens.model;

import java.math.BigInteger;

/**
 * 解码后的 ERC-20 Transfer 事件。
 */
public record TransferEvent(
        String tokenAddress,
        String from,
        String to,
        BigInteger valueWei,
        BigInteger logIndex
) {
}

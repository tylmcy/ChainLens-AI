package io.chainlens.model;

import java.math.BigInteger;

/**
 * 区块（时间戳为 Unix 秒）。
 */
public record EthBlock(
        BigInteger number,
        long timestamp,
        BigInteger baseFeePerGas,
        String rawJson
) {
}

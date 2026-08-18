package io.chainlens.model;

import java.math.BigInteger;

/**
 * 交易本体。金额一律 BigInteger（wei），禁止浮点。
 *
 * @param to 合约创建交易的 to 为 null
 * @param rawJson 节点返回的原始 JSON 文本（证据链要求，见设计文档 5.4）
 */
public record EthTransaction(
        String hash,
        String from,
        String to,
        BigInteger value,
        BigInteger gasPrice,
        BigInteger maxFeePerGas,
        BigInteger maxPriorityFeePerGas,
        BigInteger gas,
        BigInteger nonce,
        int type,
        String input,
        BigInteger blockNumber,
        String rawJson
) {
}

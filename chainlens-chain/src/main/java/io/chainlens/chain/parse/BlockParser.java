package io.chainlens.chain.parse;

import com.fasterxml.jackson.databind.JsonNode;

import io.chainlens.chain.Hexes;
import io.chainlens.model.EthBlock;

/**
 * eth_getBlockByNumber 响应 → {@link EthBlock}。
 * baseFeePerGas 在 pre-London 区块缺失时为 null。
 */
public final class BlockParser {

    public EthBlock parse(JsonNode node) {
        JsonNode number = node.get("number");
        JsonNode timestamp = node.get("timestamp");
        if (number == null || timestamp == null) {
            throw new IllegalArgumentException("区块缺少 number/timestamp");
        }
        return new EthBlock(
                Hexes.hexToBigInteger(number.asText()),
                Hexes.hexToLong(timestamp.asText()),
                node.hasNonNull("baseFeePerGas") ? Hexes.hexToBigInteger(node.get("baseFeePerGas").asText()) : null,
                node.toString()
        );
    }
}

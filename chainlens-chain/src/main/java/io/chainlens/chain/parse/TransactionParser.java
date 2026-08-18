package io.chainlens.chain.parse;

import com.fasterxml.jackson.databind.JsonNode;

import io.chainlens.chain.Hexes;
import io.chainlens.model.EthTransaction;

/**
 * eth_getTransactionByHash 响应 → {@link EthTransaction}。
 * 缺失字段（如 legacy 交易的 maxFeePerGas）解析为 null，不猜测。
 */
public final class TransactionParser {

    public EthTransaction parse(JsonNode node) {
        return new EthTransaction(
                text(node, "hash"),
                text(node, "from"),
                nullableText(node, "to"),
                quantity(node, "value"),
                quantity(node, "gasPrice"),
                quantity(node, "maxFeePerGas"),
                quantity(node, "maxPriorityFeePerGas"),
                quantity(node, "gas"),
                quantity(node, "nonce"),
                quantity(node, "type") == null ? 0 : quantity(node, "type").intValue(),
                nullableText(node, "input"),
                quantity(node, "blockNumber"),
                node.toString()
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("交易字段缺失: " + field);
        }
        return value.asText();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static java.math.BigInteger quantity(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : Hexes.hexToBigInteger(value.asText());
    }
}

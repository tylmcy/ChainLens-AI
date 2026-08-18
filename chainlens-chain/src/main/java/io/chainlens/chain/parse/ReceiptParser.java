package io.chainlens.chain.parse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.chainlens.chain.Hexes;
import io.chainlens.model.EthLog;
import io.chainlens.model.EthReceipt;

/**
 * eth_getTransactionReceipt 响应 → {@link EthReceipt}。
 * status 原样保留（0x1/0x0），成败判定由 {@link EthReceipt#isSuccess()} 完成。
 */
public final class ReceiptParser {

    public EthReceipt parse(JsonNode node) {
        BigInteger gasUsed = quantity(node, "gasUsed");
        if (gasUsed == null) {
            throw new IllegalArgumentException("回执缺少 gasUsed");
        }
        List<EthLog> logs = new ArrayList<>();
        JsonNode logsNode = node.get("logs");
        if (logsNode != null && logsNode.isArray()) {
            for (JsonNode logNode : logsNode) {
                logs.add(parseLog(logNode));
            }
        }
        return new EthReceipt(
                text(node, "transactionHash"),
                text(node, "status"),
                gasUsed,
                quantity(node, "effectiveGasPrice"),
                nullableText(node, "contractAddress"),
                logs,
                quantity(node, "blockNumber"),
                node.toString()
        );
    }

    private EthLog parseLog(JsonNode node) {
        List<String> topics = new ArrayList<>();
        JsonNode topicsNode = node.get("topics");
        if (topicsNode != null && topicsNode.isArray()) {
            topicsNode.forEach(t -> topics.add(t.asText()));
        }
        return new EthLog(
                text(node, "address"),
                topics,
                nullableText(node, "data"),
                quantity(node, "logIndex"),
                node.path("removed").asBoolean(false)
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("回执字段缺失: " + field);
        }
        return value.asText();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static BigInteger quantity(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : Hexes.hexToBigInteger(value.asText());
    }
}

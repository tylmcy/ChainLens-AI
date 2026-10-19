package io.chainlens.evaluation;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.chain.rpc.RpcProtocolException;
import io.chainlens.model.Hexes;

/**
 * 从本地冻结 JSON-RPC 响应回放取证接口，不创建 HTTP 客户端或网络连接。
 * 缺失响应和参数不匹配都视为 fixture 错误，不能替换为默认链上事实。
 */
public final class FixtureEthRpcClient implements EthRpcClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String caseId;
    private final String txHash;
    private final JsonNode chainIdResult;
    private final JsonNode transaction;
    private final JsonNode receipt;
    private final JsonNode block;
    private final JsonNode code;

    public FixtureEthRpcClient(String caseId, String txHash, Path directory) {
        this.caseId = caseId;
        this.txHash = txHash;
        chainIdResult = readResult(directory.resolve("chain-id.json"), "chain-id");
        transaction = readResult(directory.resolve("transaction.json"), "transaction");
        receipt = readResult(directory.resolve("receipt.json"), "receipt");
        block = readResult(directory.resolve("block.json"), "block");
        // code 响应只在 to 非空且非零地址时必需；合约创建仍允许目录包含 code.json。
        String to = transaction.path("to").isNull() ? null : transaction.path("to").asText(null);
        boolean needsCode = to != null && !to.equalsIgnoreCase("0x0000000000000000000000000000000000000000");
        code = needsCode ? readResult(directory.resolve("code.json"), "code", true) : null;
        verifyIdentity();
    }

    /** 仅接受 JSON-RPC 2.0 envelope；错误响应保留为明确失败。 */
    private JsonNode readResult(Path file, String kind) {
        return readResult(file, kind, false);
    }

    private JsonNode readResult(Path file, String kind, boolean allowError) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(caseId + " 缺少 fixture " + kind + ": " + file);
        }
        try {
            JsonNode envelope = MAPPER.readTree(file.toFile());
            if (allowError && envelope != null && "2.0".equals(envelope.path("jsonrpc").asText())
                    && envelope.has("error")) {
                return envelope;
            }
            if (envelope == null || !"2.0".equals(envelope.path("jsonrpc").asText())
                    || !envelope.has("result") || envelope.get("result").isNull()) {
                throw new IllegalArgumentException(caseId + " fixture " + kind + " 缺少有效 JSON-RPC result: " + file);
            }
            return envelope.get("result");
        } catch (IOException e) {
            throw new IllegalArgumentException(caseId + " 无法读取 fixture " + kind + ": " + file, e);
        }
    }

    private void verifyIdentity() {
        requireText(transaction, "from", "transaction");
        requireText(transaction, "value", "transaction");
        requireText(transaction, "input", "transaction");
        requireText(transaction, "gas", "transaction");
        requireText(transaction, "type", "transaction");
        requireText(receipt, "status", "receipt");
        requireText(receipt, "gasUsed", "receipt");
        if (!receipt.path("logs").isArray()) {
            throw new IllegalArgumentException(caseId + " fixture receipt.logs 缺失或不是数组");
        }
        requireText(block, "timestamp", "block");
        if (!txHash.equalsIgnoreCase(transaction.path("hash").asText())
                || !txHash.equalsIgnoreCase(receipt.path("transactionHash").asText())) {
            throw new IllegalArgumentException(caseId + " fixture 交易哈希与清单不一致");
        }
        BigInteger txBlock = quantity(transaction.path("blockNumber"), "transaction.blockNumber");
        BigInteger receiptBlock = quantity(receipt.path("blockNumber"), "receipt.blockNumber");
        BigInteger blockNumber = quantity(block.path("number"), "block.number");
        if (!txBlock.equals(receiptBlock) || !txBlock.equals(blockNumber)) {
            throw new IllegalArgumentException(caseId + " fixture 区块号不一致");
        }
    }

    private void requireText(JsonNode node, String field, String kind) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual()) {
            throw new IllegalArgumentException(caseId + " fixture " + kind + "." + field + " 缺失");
        }
    }

    private BigInteger quantity(JsonNode node, String field) {
        String value = node.asText();
        if (!value.matches("0x[0-9a-fA-F]+")) {
            throw new IllegalArgumentException(caseId + " fixture " + field + " 不是十六进制数量");
        }
        return Hexes.hexToBigInteger(value);
    }

    @Override
    public long chainId() {
        return quantity(chainIdResult, "chainId").longValueExact();
    }

    @Override
    public JsonNode getTransactionByHash(String requestedHash) {
        requireHash(requestedHash);
        return transaction.deepCopy();
    }

    @Override
    public JsonNode getTransactionReceipt(String requestedHash) {
        requireHash(requestedHash);
        return receipt.deepCopy();
    }

    @Override
    public JsonNode getBlockByNumber(BigInteger requestedBlock) {
        if (!blockNumber().equals(requestedBlock)) {
            throw new RpcProtocolException(caseId + " 请求了 fixture 之外的区块号: " + requestedBlock);
        }
        return block.deepCopy();
    }

    @Override
    public JsonNode getCode(String address, BigInteger requestedBlock) {
        String expectedTo = transaction.path("to").asText("");
        if (code == null || !expectedTo.toLowerCase(Locale.ROOT).equals(address.toLowerCase(Locale.ROOT))
                || !blockNumber().equals(requestedBlock)) {
            throw new RpcProtocolException(caseId + " 请求了 fixture 之外的合约代码");
        }
        if (code.has("error")) {
            throw new RpcProtocolException(caseId + " 历史 code 不可查: " + code.path("error").path("message").asText());
        }
        return code.deepCopy();
    }

    @Override
    public BigInteger blockNumber() {
        return quantity(block.path("number"), "block.number");
    }

    private void requireHash(String requestedHash) {
        if (!txHash.equalsIgnoreCase(requestedHash)) {
            throw new RpcProtocolException(caseId + " 请求了 fixture 之外的交易哈希: " + requestedHash);
        }
    }
}

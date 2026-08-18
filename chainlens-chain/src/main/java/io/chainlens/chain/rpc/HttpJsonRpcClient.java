package io.chainlens.chain.rpc;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.chainlens.chain.Hexes;

/**
 * JSON-RPC 2.0 over HTTPS 实现（Java HttpClient + Jackson）。
 *
 * <p>行为约定（对应设计文档 3.1 阶段 1 与 9.2）：
 * <ul>
 *   <li>连接超时 3s / 请求超时 10s（可配置）；</li>
 *   <li>瞬态失败（IO 中断、HTTP 429、5xx）指数退避重试，共 {@link RpcConfig#maxAttempts()} 次尝试；</li>
 *   <li>HTTP 2xx 内的 JSON-RPC error 对象：不可重试，直接抛异常；</li>
 *   <li>result == null：getTransactionByHash/getTransactionReceipt 抛
 *       {@link TransactionNotFoundException}（交易不存在的唯一判定来源）。</li>
 * </ul>
 */
public class HttpJsonRpcClient implements EthRpcClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RpcConfig config;
    private final HttpClient httpClient;
    private final AtomicLong requestIds = new AtomicLong();

    public HttpJsonRpcClient(RpcConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .build();
    }

    @Override
    public long chainId() {
        JsonNode result = callForResult("eth_chainId", List.of());
        return Hexes.hexToLong(result.asText());
    }

    @Override
    public JsonNode getTransactionByHash(String txHash) {
        JsonNode result = callForResult("eth_getTransactionByHash", List.of(txHash));
        if (result == null || result.isNull()) {
            throw new TransactionNotFoundException(txHash);
        }
        return result;
    }

    @Override
    public JsonNode getTransactionReceipt(String txHash) {
        JsonNode result = callForResult("eth_getTransactionReceipt", List.of(txHash));
        if (result == null || result.isNull()) {
            throw new TransactionNotFoundException(txHash);
        }
        return result;
    }

    @Override
    public JsonNode getBlockByNumber(BigInteger blockNumber) {
        return callForResult("eth_getBlockByNumber", List.of(Hexes.toQuantity(blockNumber), false));
    }

    @Override
    public JsonNode getCode(String address, BigInteger blockNumber) {
        return callForResult("eth_getCode", List.of(address, Hexes.toQuantity(blockNumber)));
    }

    @Override
    public BigInteger blockNumber() {
        JsonNode result = callForResult("eth_blockNumber", List.of());
        return Hexes.hexToBigInteger(result.asText());
    }

    /**
     * 带重试的调用：返回 result 节点（可能为 null，由调用方决定语义）。
     * 抛出的异常保证是 {@link RpcException} 的子类。
     */
    private JsonNode callForResult(String method, List<Object> params) {
        RpcException lastFailure = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                return executeOnce(method, params);
            } catch (RpcException e) {
                lastFailure = e;
                if (!e.isRetryable() || attempt == config.maxAttempts()) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
        // 不可达（理论上到不了这里，防御性兜底）
        throw new RpcUnavailableException("RPC 调用失败: " + method, lastFailure);
    }

    private JsonNode executeOnce(String method, List<Object> params) {
        long id = requestIds.incrementAndGet();
        String body;
        try {
            body = MAPPER.writeValueAsString(java.util.Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "method", method,
                    "params", params));
        } catch (IOException e) {
            // 参数是纯字符串/数字，序列化不可能失败
            throw new IllegalStateException("JSON-RPC 请求序列化失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl()))
                .timeout(config.requestTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RpcUnavailableException("RPC 节点不可达: " + config.baseUrl(), e);
        }

        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            throw new RpcUnavailableException("RPC 节点返回 HTTP " + status + "（重试后仍失败将映射 503）");
        }
        if (status != 200) {
            throw new RpcProtocolException("RPC 节点返回非预期 HTTP 状态: " + status);
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new RpcProtocolException("RPC 响应不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new RpcProtocolException("RPC 响应不是 JSON 对象");
        }
        JsonNode idNode = root.get("id");
        if (idNode == null || idNode.asLong() != id) {
            throw new RpcProtocolException("RPC 响应 id 不匹配");
        }
        if (root.hasNonNull("error")) {
            JsonNode error = root.get("error");
            int code = error.path("code").asInt(Integer.MIN_VALUE);
            String message = error.path("message").asText("unknown");
            boolean retryable = code == -32005 || code == -32603 && message.contains("limit");
            throw new RpcException("RPC 错误 [" + method + "]: code=" + code + ", " + message, retryable);
        }
        return root.get("result");
    }

    private void sleepBeforeRetry(int failedAttempt) {
        try {
            Thread.sleep(config.backoffBase().toMillis() * (1L << (failedAttempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcUnavailableException("重试等待被中断", e);
        }
    }
}

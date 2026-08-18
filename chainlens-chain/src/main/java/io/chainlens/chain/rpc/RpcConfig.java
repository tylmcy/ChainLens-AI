package io.chainlens.chain.rpc;

import java.time.Duration;

/**
 * RPC 客户端配置。超时与重试遵循设计文档 9.2：连接 3s / 读取 10s，
 * 指数退避重试 3 次（共 3 次尝试）。
 */
public record RpcConfig(
        String baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxAttempts,
        Duration backoffBase
) {

    public static RpcConfig defaults(String baseUrl) {
        return new RpcConfig(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10), 3, Duration.ofMillis(300));
    }
}

package io.chainlens.mcp;

import java.util.UUID;

import org.slf4j.MDC;

import io.chainlens.chain.rpc.RpcException;
import io.chainlens.chain.rpc.RpcUnavailableException;
import io.chainlens.chain.rpc.TransactionNotFoundException;

/** 下游异常到稳定、安全 MCP 错误的唯一映射入口。 */
public final class McpErrorMapper {

    private McpErrorMapper() {
    }

    public static ChainLensMcpException map(Throwable error) {
        if (error instanceof ChainLensMcpException mapped) {
            return mapped;
        }
        if (error instanceof TransactionNotFoundException) {
            return clientError(McpErrorCode.TRANSACTION_NOT_FOUND,
                    "交易不存在，Ethereum 节点返回空结果", false);
        }
        if (error instanceof RpcUnavailableException) {
            return clientError(McpErrorCode.RPC_UNAVAILABLE,
                    "Ethereum RPC 暂时不可用，请稍后重试", true);
        }
        if (error instanceof RpcException) {
            return clientError(McpErrorCode.INTERNAL_ERROR,
                    "链上数据响应未通过协议校验", false);
        }
        return clientError(McpErrorCode.INTERNAL_ERROR,
                "ChainLens 内部处理失败，请根据 traceId 排查", false);
    }

    static ChainLensMcpException clientError(McpErrorCode code, String message, boolean retryable) {
        return new ChainLensMcpException(code, message, retryable, traceId());
    }

    private static String traceId() {
        String current = MDC.get("traceId");
        return current == null || current.isBlank() ? UUID.randomUUID().toString() : current;
    }
}

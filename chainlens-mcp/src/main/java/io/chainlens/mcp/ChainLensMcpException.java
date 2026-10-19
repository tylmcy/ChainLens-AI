package io.chainlens.mcp;

/**
 * 对外可见的 MCP 工具异常。
 *
 * <p>字段保持结构化，message 只拼接安全摘要，绝不附带下游堆栈、RPC URL 或密钥。
 */
public final class ChainLensMcpException extends RuntimeException {

    private final McpErrorCode code;
    private final boolean retryable;
    private final String traceId;

    public ChainLensMcpException(McpErrorCode code, String message, boolean retryable, String traceId) {
        super(code + ": " + message + " (traceId=" + traceId + ")");
        this.code = code;
        this.retryable = retryable;
        this.traceId = traceId;
    }

    public McpErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public String traceId() {
        return traceId;
    }
}

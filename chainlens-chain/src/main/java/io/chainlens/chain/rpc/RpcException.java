package io.chainlens.chain.rpc;

/**
 * RPC 层异常基类。retryable 表示该错误是否值得重试
 * （瞬态：IO 中断、429、5xx；非瞬态：协议错误、参数错误）。
 * 上层的 HTTP 状态映射（404/503/500）在 chainlens-api 完成（M3）。
 */
public class RpcException extends RuntimeException {

    private final boolean retryable;

    public RpcException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public RpcException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

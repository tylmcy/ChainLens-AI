package io.chainlens.chain.rpc;

/**
 * 节点不可达 / 限流：重试耗尽后抛出。上层映射 503 RPC_UNAVAILABLE，绝不伪造结果。
 */
public class RpcUnavailableException extends RpcException {

    public RpcUnavailableException(String message, Throwable cause) {
        super(message, true, cause);
    }

    public RpcUnavailableException(String message) {
        super(message, true);
    }
}

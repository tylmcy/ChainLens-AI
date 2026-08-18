package io.chainlens.chain.rpc;

/**
 * 响应不符合 JSON-RPC 2.0 协议（无法解析、缺字段、id 不匹配）。
 * 属于编程/节点实现错误，不重试。
 */
public class RpcProtocolException extends RpcException {

    public RpcProtocolException(String message) {
        super(message, false);
    }
}

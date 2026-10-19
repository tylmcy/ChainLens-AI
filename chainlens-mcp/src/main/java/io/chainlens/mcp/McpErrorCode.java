package io.chainlens.mcp;

/** MCP 工具稳定错误分类；客户端只能依赖这些代码，不依赖异常类名。 */
public enum McpErrorCode {
    INVALID_INPUT,
    TRANSACTION_NOT_FOUND,
    RPC_UNAVAILABLE,
    TOO_MANY_REQUESTS,
    KNOWLEDGE_UNAVAILABLE,
    INTERNAL_ERROR
}

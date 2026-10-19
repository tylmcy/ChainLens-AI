package io.chainlens.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;

import io.chainlens.chain.rpc.RpcException;
import io.chainlens.chain.rpc.RpcProtocolException;
import io.chainlens.chain.rpc.RpcUnavailableException;
import io.chainlens.chain.rpc.TransactionNotFoundException;

/**
 * 异常 → ErrorResponse 的统一映射（设计文档 3.4 / 9.1）。
 * REST 异常处理器与 SSE 桥共用，保证两条路径的错误契约一致。
 */
public final class ErrorMapper {

    public static final String CODE_INVALID_INPUT = "INVALID_INPUT";
    public static final String CODE_TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String CODE_ANALYSIS_NOT_FOUND = "ANALYSIS_NOT_FOUND";
    public static final String CODE_NOT_FOUND = "NOT_FOUND";
    public static final String CODE_TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    public static final String CODE_RPC_UNAVAILABLE = "RPC_UNAVAILABLE";
    public static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorMapper() {
    }

    public static ErrorResponse map(Throwable error) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("traceId", MDC.get("traceId"));
        if (error instanceof InvalidInputException) {
            return ErrorResponse.of(CODE_INVALID_INPUT, error.getMessage(), false, details);
        }
        if (error instanceof TransactionNotFoundException) {
            return ErrorResponse.of(CODE_TRANSACTION_NOT_FOUND, error.getMessage(), false, details);
        }
        if (error instanceof AnalysisNotFoundException) {
            return ErrorResponse.of(CODE_ANALYSIS_NOT_FOUND, error.getMessage(), false, details);
        }
        if (error instanceof TooManyRequestsException) {
            return ErrorResponse.of(CODE_TOO_MANY_REQUESTS, error.getMessage(), true, details);
        }
        if (error instanceof RpcUnavailableException) {
            return ErrorResponse.of(CODE_RPC_UNAVAILABLE, error.getMessage(), true, details);
        }
        if (error instanceof RpcProtocolException) {
            return ErrorResponse.of(CODE_INTERNAL_ERROR, "RPC 协议错误：" + error.getMessage(), false, details);
        }
        if (error instanceof RpcException rpc && !rpc.isRetryable()) {
            return ErrorResponse.of(CODE_INTERNAL_ERROR, "链上数据查询失败：" + error.getMessage(), false, details);
        }
        // LLM/数据库等未知异常可能包含连接串或 Key；客户端只接收稳定错误码与 traceId。
        return ErrorResponse.of(CODE_INTERNAL_ERROR, "内部错误，请使用 traceId 排查", false, details);
    }
}

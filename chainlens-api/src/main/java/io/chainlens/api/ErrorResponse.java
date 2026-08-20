package io.chainlens.api;

import java.util.Map;

/**
 * 统一错误契约（设计文档 3.4 / 9.1）：所有端点一致。
 */
public record ErrorResponse(
        String code,
        String message,
        boolean retryable,
        Map<String, Object> details
) {

    public static ErrorResponse of(String code, String message, boolean retryable, Map<String, Object> details) {
        return new ErrorResponse(code, message, retryable, details == null ? Map.of() : details);
    }
}

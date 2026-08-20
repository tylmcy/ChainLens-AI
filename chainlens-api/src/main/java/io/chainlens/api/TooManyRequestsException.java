package io.chainlens.api;

/**
 * 并发超限（设计文档 3.7）：超出上限返回 429 + Retry-After。
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}

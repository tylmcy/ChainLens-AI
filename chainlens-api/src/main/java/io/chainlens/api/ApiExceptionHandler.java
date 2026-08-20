package io.chainlens.api;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.chainlens.chain.rpc.RpcProtocolException;
import io.chainlens.chain.rpc.RpcUnavailableException;
import io.chainlens.chain.rpc.TransactionNotFoundException;

/**
 * REST 错误契约（设计文档 3.4 映射表）：统一 ErrorResponse。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ErrorResponse> invalidInput(InvalidInputException e) {
        return build(HttpStatus.BAD_REQUEST, ErrorMapper.map(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST,
                ErrorResponse.of(ErrorMapper.CODE_INVALID_INPUT, "请求参数非法：" + message, false, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST,
                ErrorResponse.of(ErrorMapper.CODE_INVALID_INPUT, "请求体无法解析", false, null));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ResponseEntity<ErrorResponse> transactionNotFound(TransactionNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, ErrorMapper.map(e));
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    ResponseEntity<ErrorResponse> analysisNotFound(AnalysisNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, ErrorMapper.map(e));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ErrorResponse> tooMany(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "5")
                .body(ErrorMapper.map(e));
    }

    @ExceptionHandler(RpcUnavailableException.class)
    ResponseEntity<ErrorResponse> rpcUnavailable(RpcUnavailableException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ErrorMapper.map(e));
    }

    @ExceptionHandler(RpcProtocolException.class)
    ResponseEntity<ErrorResponse> rpcProtocol(RpcProtocolException e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMapper.map(e));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMapper.map(e));
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}

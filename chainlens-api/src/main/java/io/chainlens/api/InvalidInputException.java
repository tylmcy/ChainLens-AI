package io.chainlens.api;

/**
 * 输入非法（哈希格式错误 / 提取失败）。
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}

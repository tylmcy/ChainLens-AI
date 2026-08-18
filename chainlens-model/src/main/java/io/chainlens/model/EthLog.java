package io.chainlens.model;

import java.math.BigInteger;
import java.util.List;

/**
 * 事件日志（receipt.logs 条目）。topics 保留原始 hex（含 0x 前缀）。
 */
public record EthLog(
        String address,
        List<String> topics,
        String data,
        BigInteger logIndex,
        boolean removed
) {
}

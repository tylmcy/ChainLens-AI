package io.chainlens.model;

import java.math.BigInteger;
import java.util.List;

/**
 * 交易回执。status 是成败判定的唯一依据（0x1 成功 / 0x0 失败）。
 */
public record EthReceipt(
        String transactionHash,
        String status,
        BigInteger gasUsed,
        BigInteger effectiveGasPrice,
        String contractAddress,
        List<EthLog> logs,
        BigInteger blockNumber,
        String rawJson
) {

    /** 0x1 为成功，其余（含异常值）一律视为失败。 */
    public boolean isSuccess() {
        return "0x1".equalsIgnoreCase(status);
    }
}

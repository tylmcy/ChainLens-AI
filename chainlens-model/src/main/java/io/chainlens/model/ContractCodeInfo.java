package io.chainlens.model;

import java.math.BigInteger;

/**
 * 合约代码检查结果（eth_getCode）。
 *
 * <p>必须携带交易所在区块号查询（默认 latest 读到的是今天的状态，与交易发生时
 * 可能不一致）；节点不支持历史状态查询时 available=false，如实记录原因，
 * 依赖代码的规则不得触发（设计文档 5.3 / 6.2）。
 */
public record ContractCodeInfo(
        String address,
        boolean available,
        boolean hasCode,
        BigInteger codeSize,
        BigInteger queriedAtBlock,
        String unavailableReason
) {

    public static ContractCodeInfo unavailable(String address, BigInteger queriedAtBlock, String reason) {
        return new ContractCodeInfo(address, false, false, null, queriedAtBlock, reason);
    }
}

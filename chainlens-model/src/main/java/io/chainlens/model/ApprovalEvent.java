package io.chainlens.model;

import java.math.BigInteger;

/**
 * 解码后的 ERC-20 Approval 事件。
 */
public record ApprovalEvent(
        String tokenAddress,
        String owner,
        String spender,
        BigInteger valueWei,
        BigInteger logIndex
) {
}

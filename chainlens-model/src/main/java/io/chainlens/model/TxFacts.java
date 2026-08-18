package io.chainlens.model;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * 取证案卷：阶段 A（强制取证 + 解析）的统一产物，规则引擎与 LLM 的唯一输入。
 *
 * <p>net 金额为 BigInteger（wei）；fromNetWei 为负值表示净支出；toNetWei
 * 仅在交易成功时等于 value（失败交易资金未转出），见设计文档 5.3。
 */
public record TxFacts(
        Network network,
        EthTransaction transaction,
        EthReceipt receipt,
        EthBlock block,
        BigInteger gasCostWei,
        BigInteger valueWei,
        BigInteger fromNetWei,
        BigInteger toNetWei,
        List<TransferEvent> transfers,
        List<ApprovalEvent> approvals,
        List<String> unknownLogTopics,
        CalldataAnalysis calldata,
        ContractCodeInfo contractCode,
        Instant fetchedAt
) {
}

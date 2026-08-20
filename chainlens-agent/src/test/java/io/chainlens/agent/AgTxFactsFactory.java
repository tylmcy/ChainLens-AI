package io.chainlens.agent;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.EthBlock;
import io.chainlens.model.EthLog;
import io.chainlens.model.EthReceipt;
import io.chainlens.model.EthTransaction;
import io.chainlens.model.Network;
import io.chainlens.model.TransferEvent;
import io.chainlens.model.TxFacts;

/**
 * agent 模块测试用 TxFacts 构造器（最小化，只声明测试关心的字段）。
 */
public final class AgTxFactsFactory {

    public static final String FROM = "0x1111111111111111111111111111111111111111";
    public static final String TO = "0x3333333333333333333333333333333333333333";
    public static final String TOKEN = "0x5555555555555555555555555555555555555555";
    public static final String TX_HASH = "0xabc0000000000000000000000000000000000000000000000000000000000001";
    public static final BigInteger TWO_256_MINUS_1 = new BigInteger("2").pow(256).subtract(BigInteger.ONE);

    private EthTransaction transaction = new EthTransaction(
            TX_HASH, FROM, TO, BigInteger.ZERO, BigInteger.valueOf(1_000_000_000L),
            null, null, BigInteger.valueOf(21000), BigInteger.ZERO, 0, "0x", BigInteger.ONE, "{}");
    private EthReceipt receipt = new EthReceipt(
            TX_HASH, "0x1", BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L),
            null, List.of(), BigInteger.ONE, "{}");
    private List<TransferEvent> transfers = List.of();
    private List<ApprovalEvent> approvals = List.of();
    private CalldataAnalysis calldata = new CalldataAnalysis(false, null, List.of(), null,
            CalldataAnalysis.Status.UNRESOLVED);
    private ContractCodeInfo contractCode = ContractCodeInfo.unavailable(TO, BigInteger.ONE, "测试默认不可查");

    public static AgTxFactsFactory create() {
        return new AgTxFactsFactory();
    }

    public AgTxFactsFactory withStatus(String status) {
        receipt = new EthReceipt(receipt.transactionHash(), status, receipt.gasUsed(),
                receipt.effectiveGasPrice(), receipt.contractAddress(), receipt.logs(),
                receipt.blockNumber(), receipt.rawJson());
        return this;
    }

    public AgTxFactsFactory withInput(String input) {
        transaction = new EthTransaction(transaction.hash(), transaction.from(), transaction.to(),
                transaction.value(), transaction.gasPrice(), transaction.maxFeePerGas(),
                transaction.maxPriorityFeePerGas(), transaction.gas(), transaction.nonce(),
                transaction.type(), input, transaction.blockNumber(), transaction.rawJson());
        return this;
    }

    public AgTxFactsFactory withTransfers(List<TransferEvent> transfers) {
        this.transfers = transfers;
        return this;
    }

    public AgTxFactsFactory withApprovals(List<ApprovalEvent> approvals) {
        this.approvals = approvals;
        return this;
    }

    public AgTxFactsFactory withCalldata(CalldataAnalysis calldata) {
        this.calldata = calldata;
        return this;
    }

    public AgTxFactsFactory withContractCode(ContractCodeInfo contractCode) {
        this.contractCode = contractCode;
        return this;
    }

    public TxFacts build() {
        BigInteger gasCost = receipt.gasUsed().multiply(
                receipt.effectiveGasPrice() == null ? BigInteger.ZERO : receipt.effectiveGasPrice());
        BigInteger value = transaction.value() == null ? BigInteger.ZERO : transaction.value();
        return new TxFacts(
                Network.ANVIL, transaction, receipt,
                new EthBlock(BigInteger.ONE, 1700000000L, null, "{}"),
                gasCost, value, value.add(gasCost).negate(), value,
                transfers, approvals, List.of(), calldata, contractCode, Instant.EPOCH);
    }
}

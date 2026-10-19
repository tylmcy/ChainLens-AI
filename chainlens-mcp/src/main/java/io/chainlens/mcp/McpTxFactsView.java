package io.chainlens.mcp;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 * MCP 专用取证视图。领域模型中的 null 有链上语义（如 legacy 交易没有 EIP-1559 fee），
 * 这里使用可空注解和 NON_NULL 序列化，避免为了通过 MCP schema 而伪造 0 或空字符串。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpTxFactsView(
        Network network,
        TransactionView transaction,
        ReceiptView receipt,
        BlockView block,
        BigInteger gasCostWei,
        BigInteger valueWei,
        BigInteger fromNetWei,
        BigInteger toNetWei,
        List<TransferView> transfers,
        List<ApprovalView> approvals,
        List<String> unknownLogTopics,
        CalldataView calldata,
        ContractCodeView contractCode,
        Instant fetchedAt
) {

    public static McpTxFactsView from(TxFacts facts) {
        return new McpTxFactsView(facts.network(), TransactionView.from(facts.transaction()),
                ReceiptView.from(facts.receipt()), BlockView.from(facts.block()), facts.gasCostWei(),
                facts.valueWei(), facts.fromNetWei(), facts.toNetWei(),
                facts.transfers().stream().map(TransferView::from).toList(),
                facts.approvals().stream().map(ApprovalView::from).toList(),
                List.copyOf(facts.unknownLogTopics()), CalldataView.from(facts.calldata()),
                ContractCodeView.from(facts.contractCode()), facts.fetchedAt());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionView(
            String hash, String from, @Nullable String to, BigInteger value,
            @Nullable BigInteger gasPrice, @Nullable BigInteger maxFeePerGas,
            @Nullable BigInteger maxPriorityFeePerGas, BigInteger gas, BigInteger nonce,
            int type, String input, @Nullable BigInteger blockNumber, String rawJson
    ) {
        static TransactionView from(EthTransaction tx) {
            return new TransactionView(tx.hash(), tx.from(), tx.to(), tx.value(), tx.gasPrice(),
                    tx.maxFeePerGas(), tx.maxPriorityFeePerGas(), tx.gas(), tx.nonce(), tx.type(),
                    tx.input(), tx.blockNumber(), tx.rawJson());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReceiptView(
            String transactionHash, String status, BigInteger gasUsed,
            @Nullable BigInteger effectiveGasPrice, @Nullable String contractAddress,
            List<LogView> logs, BigInteger blockNumber, String rawJson
    ) {
        static ReceiptView from(EthReceipt receipt) {
            return new ReceiptView(receipt.transactionHash(), receipt.status(), receipt.gasUsed(),
                    receipt.effectiveGasPrice(), receipt.contractAddress(),
                    receipt.logs().stream().map(LogView::from).toList(), receipt.blockNumber(),
                    receipt.rawJson());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BlockView(BigInteger number, long timestamp,
                            @Nullable BigInteger baseFeePerGas, String rawJson) {
        static BlockView from(EthBlock block) {
            return new BlockView(block.number(), block.timestamp(), block.baseFeePerGas(), block.rawJson());
        }
    }

    public record LogView(String address, List<String> topics, String data,
                          BigInteger logIndex, boolean removed) {
        static LogView from(EthLog log) {
            return new LogView(log.address(), List.copyOf(log.topics()), log.data(), log.logIndex(), log.removed());
        }
    }

    public record TransferView(String tokenAddress, String from, String to,
                               BigInteger valueWei, BigInteger logIndex) {
        static TransferView from(TransferEvent event) {
            return new TransferView(event.tokenAddress(), event.from(), event.to(),
                    event.valueWei(), event.logIndex());
        }
    }

    public record ApprovalView(String tokenAddress, String owner, String spender,
                               BigInteger valueWei, BigInteger logIndex) {
        static ApprovalView from(ApprovalEvent event) {
            return new ApprovalView(event.tokenAddress(), event.owner(), event.spender(),
                    event.valueWei(), event.logIndex());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CalldataView(boolean hasInput, @Nullable String selector, List<String> candidates,
                               @Nullable String resolvedSignature, CalldataAnalysis.Status status) {
        static CalldataView from(CalldataAnalysis calldata) {
            return new CalldataView(calldata.hasInput(), calldata.selector(),
                    List.copyOf(calldata.candidates()), calldata.resolvedSignature(), calldata.status());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContractCodeView(String address, boolean available, boolean hasCode,
                                   @Nullable BigInteger codeSize, BigInteger queriedAtBlock,
                                   @Nullable String unavailableReason) {
        static ContractCodeView from(ContractCodeInfo code) {
            return new ContractCodeView(code.address(), code.available(), code.hasCode(), code.codeSize(),
                    code.queriedAtBlock(), code.unavailableReason());
        }
    }
}

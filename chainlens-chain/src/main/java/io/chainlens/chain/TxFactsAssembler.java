package io.chainlens.chain;

import java.math.BigInteger;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import io.chainlens.chain.parse.BlockParser;
import io.chainlens.chain.parse.CalldataDecoder;
import io.chainlens.chain.parse.EventDecoder;
import io.chainlens.chain.parse.ReceiptParser;
import io.chainlens.chain.parse.TransactionParser;
import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.chain.rpc.RpcException;
import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.EthBlock;
import io.chainlens.model.EthReceipt;
import io.chainlens.model.EthTransaction;
import io.chainlens.model.Network;
import io.chainlens.model.Hexes;
import io.chainlens.model.TxFacts;

/**
 * 阶段 A 强制取证（设计文档 3.1 阶段 1~2）：
 * eth_chainId → 交易 → 回执 → 区块 → （to 非零地址时）eth_getCode(交易所在区块)。
 *
 * <p>失败语义：交易不存在/节点不可达的 {@link RpcException} 直接上抛（由 API 层映射
 * 404/503）；仅 getCode 失败是"可容忍"的——记录为"代码不可查"，不阻断分析
 * （设计文档 9.3）。
 */
public final class TxFactsAssembler implements TxFactsProvider {

    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final EthRpcClient client;
    private final TransactionParser transactionParser;
    private final ReceiptParser receiptParser;
    private final BlockParser blockParser;
    private final EventDecoder eventDecoder;
    private final CalldataDecoder calldataDecoder;

    public TxFactsAssembler(EthRpcClient client) {
        this(client, new TransactionParser(), new ReceiptParser(), new BlockParser(),
                new EventDecoder(), new CalldataDecoder(new io.chainlens.chain.parse.CuratedSelectorRegistry()));
    }

    public TxFactsAssembler(EthRpcClient client,
                            TransactionParser transactionParser,
                            ReceiptParser receiptParser,
                            BlockParser blockParser,
                            EventDecoder eventDecoder,
                            CalldataDecoder calldataDecoder) {
        this.client = client;
        this.transactionParser = transactionParser;
        this.receiptParser = receiptParser;
        this.blockParser = blockParser;
        this.eventDecoder = eventDecoder;
        this.calldataDecoder = calldataDecoder;
    }

    @Override
    public TxFacts assemble(String txHash) {
        long chainId = client.chainId();
        Network network = Network.fromChainId(chainId);

        JsonNode txJson = client.getTransactionByHash(txHash);
        EthTransaction transaction = transactionParser.parse(txJson);

        EthReceipt receipt = receiptParser.parse(client.getTransactionReceipt(txHash));
        EthBlock block = blockParser.parse(client.getBlockByNumber(receipt.blockNumber()));

        // gas 成本：EIP-1559（type 0x2）用 effectiveGasPrice；legacy 用 gasPrice（设计文档 5.3）
        BigInteger effectivePrice = (transaction.type() == 2 && receipt.effectiveGasPrice() != null)
                ? receipt.effectiveGasPrice()
                : transaction.gasPrice();
        BigInteger gasCostWei = (effectivePrice == null || receipt.gasUsed() == null)
                ? null
                : receipt.gasUsed().multiply(effectivePrice);

        BigInteger value = transaction.value() == null ? BigInteger.ZERO : transaction.value();
        boolean success = receipt.isSuccess();
        BigInteger toNetWei = success ? value : BigInteger.ZERO;
        BigInteger fromNetWei = (gasCostWei == null ? BigInteger.ZERO : gasCostWei).add(toNetWei).negate();

        EventDecoder.DecodedEvents events = eventDecoder.decode(receipt.logs());

        ContractCodeInfo contractCode = inspectContractCode(transaction, receipt);

        return new TxFacts(
                network,
                transaction,
                receipt,
                block,
                gasCostWei,
                value,
                fromNetWei,
                toNetWei,
                events.transfers(),
                events.approvals(),
                events.unknownTopics(),
                calldataDecoder.decode(transaction.input()),
                contractCode,
                Instant.now()
        );
    }

    private ContractCodeInfo inspectContractCode(EthTransaction transaction, EthReceipt receipt) {
        if (transaction.to() == null) {
            return ContractCodeInfo.unavailable(null, receipt.blockNumber(), "合约创建交易（to 为空），无需代码检查");
        }
        String to = Hexes.normalizeAddress(transaction.to());
        if (ZERO_ADDRESS.equals(to)) {
            return ContractCodeInfo.unavailable(to, receipt.blockNumber(), "to 为零地址，无需代码检查");
        }
        try {
            JsonNode code = client.getCode(to, receipt.blockNumber());
            String hex = code.asText();
            boolean hasCode = hex != null && hex.length() > 2;
            BigInteger codeSize = hasCode
                    ? BigInteger.valueOf((hex.length() - 2) / 2L)
                    : BigInteger.ZERO;
            return new ContractCodeInfo(to, true, hasCode, codeSize, receipt.blockNumber(), null);
        } catch (RpcException e) {
            // 历史状态不可查：如实记录，依赖代码的规则不得触发（设计文档 9.3 / 6.2）
            return ContractCodeInfo.unavailable(to, receipt.blockNumber(), e.getMessage());
        }
    }
}

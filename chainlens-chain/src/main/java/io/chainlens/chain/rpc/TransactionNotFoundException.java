package io.chainlens.chain.rpc;

/**
 * 节点明确返回 null 结果（交易不存在）。与"节点不可达"严格区分（设计文档 3.4）。
 */
public class TransactionNotFoundException extends RpcException {

    public TransactionNotFoundException(String transactionHash) {
        super("交易不存在（节点明确返回 null）: " + transactionHash, false);
    }
}

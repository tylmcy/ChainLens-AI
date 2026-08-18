package io.chainlens.chain.rpc;

import java.math.BigInteger;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 只读 Ethereum JSON-RPC 客户端（薄客户端，仅本项目需要的 6 个方法）。
 *
 * <p>返回值统一为 Jackson JsonNode：保留节点原始响应（证据链要求），
 * 类型化解析由 chainlens-chain 的 parser 层完成。
 */
public interface EthRpcClient {

    /** eth_chainId → 十进制链 ID。 */
    long chainId();

    /**
     * eth_getTransactionByHash。节点明确返回 null（交易不存在）时抛
     * {@link TransactionNotFoundException}；节点不可达时抛 {@link RpcUnavailableException}。
     */
    JsonNode getTransactionByHash(String txHash);

    /** eth_getTransactionReceipt。交易不存在时抛 {@link TransactionNotFoundException}。 */
    JsonNode getTransactionReceipt(String txHash);

    /** eth_getBlockByNumber（带区块号，不依赖 latest）。 */
    JsonNode getBlockByNumber(BigInteger blockNumber);

    /**
     * eth_getCode(address, blockNumber)：必须带交易所在区块号（设计文档 5.3）。
     * 节点不支持历史状态查询时抛 {@link RpcException}（retryable=false），
     * 调用方应将其记录为"代码不可查"而非失败。
     */
    JsonNode getCode(String address, BigInteger blockNumber);

    /** eth_blockNumber（用于展示当前链高度等场景）。 */
    BigInteger blockNumber();
}

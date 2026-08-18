package io.chainlens.rules.support;

import java.math.BigInteger;

import io.chainlens.model.Evidence;
import io.chainlens.model.TxFacts;

/**
 * 规则专用 Evidence 工厂：统一从案卷生成证据定位。
 *
 * <p>rawJsonRef 使用 JSON Pointer（RFC 6901），指向完整 RPC 原始响应文档内的字段
 * （原始响应结构为 {"jsonrpc","id","result":...}），例如：
 * <ul>
 *   <li>回执状态：{@code {"rpcMethod":"eth_getTransactionReceipt","jsonPointer":"/result/status"}}；</li>
 *   <li>第 3 条日志：{@code /result/logs/2}；</li>
 *   <li>交易 calldata：{@code /result/input}；</li>
 *   <li>合约代码：{@code /result}。</li>
 * </ul>
 */
public final class RuleEvidence {

    private RuleEvidence() {
    }

    public static Evidence receipt(TxFacts facts, String jsonPointer) {
        return receipt(facts, null, jsonPointer);
    }

    public static Evidence receipt(TxFacts facts, BigInteger logIndex, String jsonPointer) {
        return evidence(facts, logIndex, "eth_getTransactionReceipt", jsonPointer);
    }

    public static Evidence transaction(TxFacts facts, String jsonPointer) {
        return evidence(facts, null, "eth_getTransactionByHash", jsonPointer);
    }

    public static Evidence code(TxFacts facts) {
        return evidence(facts, null, "eth_getCode", "/result");
    }

    private static Evidence evidence(TxFacts facts, BigInteger logIndex, String rpcMethod, String jsonPointer) {
        return new Evidence(
                BigInteger.valueOf(facts.network().chainId()),
                facts.network().label(),
                facts.receipt().blockNumber(),
                facts.transaction().hash(),
                logIndex,
                rpcMethod,
                facts.fetchedAt(),
                new Evidence.RawJsonRef(rpcMethod, jsonPointer)
        );
    }
}

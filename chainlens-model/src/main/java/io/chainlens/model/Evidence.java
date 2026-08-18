package io.chainlens.model;

import java.math.BigInteger;
import java.time.Instant;

/**
 * 证据定位：每条结论的可追溯信息。rawJsonRef 使用 JSON Pointer（RFC 6901）
 * 路径定位原始 RPC 响应中的字段（JSONB 文档无稳定行号，路径定位才可程序化验证）。
 */
public record Evidence(
        BigInteger chainId,
        String network,
        BigInteger blockNumber,
        String transactionHash,
        BigInteger logIndex,
        String rpcMethod,
        Instant fetchedAt,
        RawJsonRef rawJsonRef
) {

    /** 原始数据定位：哪个 RPC 响应、其中的哪条 JSON Pointer 路径。 */
    public record RawJsonRef(String rpcMethod, String jsonPointer) {
    }
}

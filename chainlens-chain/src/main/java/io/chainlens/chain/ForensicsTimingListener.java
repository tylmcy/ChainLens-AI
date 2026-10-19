package io.chainlens.chain;

/**
 * 单次取证的计时回调。耗时来自单调时钟，单位为纳秒；RPC 为所有 JSON-RPC 调用之和，
 * parsing 为取证期间其余解析与装配耗时。实现方不应在回调中抛异常影响事实链路。
 */
@FunctionalInterface
public interface ForensicsTimingListener {

    void onTiming(long rpcNanos, long parsingNanos);

    /** 普通分析无需采样时使用。 */
    static ForensicsTimingListener noop() {
        return (rpcNanos, parsingNanos) -> { };
    }
}

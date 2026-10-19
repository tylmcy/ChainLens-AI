package io.chainlens.evaluation;

import io.chainlens.chain.ForensicsTimingListener;
import io.chainlens.chain.TxFactsProvider;
import io.chainlens.model.TxFacts;

/**
 * Full 模式在 Agent 进入规则和 LLM 阶段之前核对取证网络。
 * 即使节点在预检后切换了 chainId，也不会把错误网络的事实交给模型。
 */
public final class NetworkGuardedFactsProvider implements TxFactsProvider {

    private final long expectedChainId;
    private final TxFactsProvider delegate;

    public NetworkGuardedFactsProvider(long expectedChainId, TxFactsProvider delegate) {
        this.expectedChainId = expectedChainId;
        this.delegate = delegate;
    }

    @Override
    public TxFacts assemble(String txHash) {
        return check(delegate.assemble(txHash));
    }

    @Override
    public TxFacts assemble(String txHash, ForensicsTimingListener timingListener) {
        return check(delegate.assemble(txHash, timingListener));
    }

    private TxFacts check(TxFacts facts) {
        if (facts == null || facts.network() == null || facts.network().chainId() != expectedChainId) {
            throw new IllegalStateException("Full 取证网络与预期 chainId=" + expectedChainId + " 不一致");
        }
        return facts;
    }
}

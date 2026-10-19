package io.chainlens.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.chainlens.agent.AnalysisProgressListener;
import io.chainlens.agent.AnalysisService;
import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.model.AnalysisResult;

/** 按清单 chainId 选择只读节点和分析服务，并阻止跨网络误取证。 */
public final class FullNetworkRouter {

    public record Endpoint(EthRpcClient rpc, AnalysisService analysisService) {
        public Endpoint {
            Objects.requireNonNull(rpc, "rpc");
            Objects.requireNonNull(analysisService, "analysisService");
        }
    }

    private final Map<Long, Endpoint> endpoints;

    public FullNetworkRouter(Map<Long, Endpoint> endpoints) {
        this.endpoints = Map.copyOf(endpoints);
    }

    /** 先校验本次所有网络，避免执行一半才发现主网节点缺失或连到错误链。 */
    public void preflight(List<EvaluationCase> selectedCases) {
        for (long chainId : selectedCases.stream().map(EvaluationCase::chainId).distinct().sorted().toList()) {
            Endpoint endpoint = endpoints.get(chainId);
            if (endpoint == null) {
                throw new IllegalStateException("Full 评测缺少 chainId=" + chainId + " 的 RPC/AnalysisService");
            }
            long observed = endpoint.rpc().chainId();
            if (observed != chainId) {
                throw new IllegalStateException("Full 评测 RPC 网络不匹配：期望 chainId=" + chainId
                        + "，实际=" + observed);
            }
        }
    }

    /** 取证后再次核对网络；若配置在执行期间变化，结果不得计为成功。 */
    public AnalysisResult analyze(EvaluationCase item, AnalysisProgressListener listener) {
        Endpoint endpoint = endpoints.get(item.chainId());
        if (endpoint == null) {
            throw new IllegalStateException(item.caseId() + " 缺少 chainId=" + item.chainId() + " 的节点");
        }
        AnalysisResult result = endpoint.analysisService().analyze(item.txHash(), listener);
        if (result == null || result.facts() == null || result.facts().network() == null
                || result.facts().network().chainId() != item.chainId()
                || result.network() == null || result.network().chainId() != item.chainId()) {
            throw new IllegalStateException(item.caseId() + " 取证结果网络与数据集 chainId 不一致");
        }
        return result;
    }
}

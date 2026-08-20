package io.chainlens.agent;

import io.chainlens.model.AnalysisResult;

/**
 * 一次交易分析的编排入口（设计文档 3.1 阶段 1~5）。
 * M3 实现 = 确定性链路（取证 → 规则 → 模板报告）；
 * M5 将在阶段 A 之后追加 LLM 工具调用阶段（护栏 + 结构化报告）。
 */
public interface AnalysisService {

    AnalysisResult analyze(String txHash, AnalysisProgressListener listener);
}

package io.chainlens.agent;

import java.util.List;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.Finding;

/**
 * 分析进度监听（SSE 事件的领域层回调，设计文档 3.2）。
 * 实现方（api 层 SseEmitter 桥）负责把回调转成 SSE 事件；
 * 同步 JSON 模式下传入 no-op 实现。
 */
public interface AnalysisProgressListener {

    /** 全部事件 no-op 的监听器（同步 JSON 模式使用）。 */
    static AnalysisProgressListener noop() {
        return new AnalysisProgressListener() {
        };
    }

    default void onStage(String stage, String message) {
    }

    default void onToolStarted(String toolName) {
    }

    default void onToolCompleted(String toolName, boolean success, long durationMs, String summary) {
    }

    default void onRulesCompleted(List<Finding> findings) {
    }

    default void onReportCompleted(AnalysisReport report) {
    }

    default void onAnalysisCompleted(AnalysisResult result) {
    }

    default void onAnalysisFailed(Throwable error) {
    }

    /** 单次分析的 RPC、解析、规则或 LLM 阶段耗时；同一阶段可多次上报。 */
    default void onSegmentTiming(String segment, long durationNanos) {
    }

    /** 一次成功 ChatModel 响应的通用 token 用量；null 表示 provider 未提供该字段。 */
    default void onModelUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    }

    /** 每次真正发送模型请求前的预算准入点；抛异常表示拒绝本次调用。 */
    default void beforeModelCall(Integer requestMaxOutputTokens) {
    }

    /** 模型调用失败时上报异常类型；失败调用的 token 用量仍视为未知。 */
    default void onModelFailure(String errorType) {
    }
}

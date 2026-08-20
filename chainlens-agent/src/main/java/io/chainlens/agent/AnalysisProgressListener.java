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

    default void onRulesCompleted(List<Finding> findings) {
    }

    default void onReportCompleted(AnalysisReport report) {
    }

    default void onAnalysisCompleted(AnalysisResult result) {
    }

    default void onAnalysisFailed(Throwable error) {
    }
}

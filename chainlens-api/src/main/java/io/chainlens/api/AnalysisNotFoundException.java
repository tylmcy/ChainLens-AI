package io.chainlens.api;

/**
 * 历史分析不存在。
 */
public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(String analysisId) {
        super("分析不存在: " + analysisId);
    }
}

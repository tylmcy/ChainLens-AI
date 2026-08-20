package io.chainlens.api;

import java.util.Optional;

import io.chainlens.model.AnalysisResult;

/**
 * 分析历史存取（设计文档 5.6：单表 JSONB + 索引列）。
 */
public interface AnalysisRepository {

    void save(AnalysisResult result);

    Optional<AnalysisResult> findById(String analysisId);
}

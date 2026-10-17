package io.chainlens.knowledge;

/** 一次幂等同步的统计结果。 */
public record KnowledgeSyncStats(int added, int updated, int deleted, int unchanged, int failed) {
}

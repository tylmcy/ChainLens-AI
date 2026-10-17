package io.chainlens.knowledge;

/** 同步失败；事务会回滚，failed 统计供管理端点与日志展示。 */
public final class KnowledgeSyncException extends RuntimeException {

    private final KnowledgeSyncStats stats;

    public KnowledgeSyncException(String message, Throwable cause) {
        super(message, cause);
        this.stats = new KnowledgeSyncStats(0, 0, 0, 0, 1);
    }

    public KnowledgeSyncStats stats() {
        return stats;
    }
}

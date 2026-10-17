package io.chainlens.knowledge;

import java.util.concurrent.atomic.AtomicReference;

/** 保存知识索引的运行状态；同步完成前检索明确返回 NOT_READY。 */
public final class KnowledgeReadiness {

    private final AtomicReference<State> state = new AtomicReference<>(new State(
            KnowledgeSearch.Availability.NOT_READY, "知识库正在等待首次同步"));

    public State current() {
        return state.get();
    }

    public void syncing() {
        state.set(new State(KnowledgeSearch.Availability.NOT_READY, "知识库正在同步"));
    }

    public void ready() {
        state.set(new State(KnowledgeSearch.Availability.AVAILABLE, ""));
    }

    public void failed(String reason) {
        state.set(new State(KnowledgeSearch.Availability.UNAVAILABLE,
                reason == null || reason.isBlank() ? "知识库同步失败" : reason));
    }

    public record State(KnowledgeSearch.Availability availability, String reason) {
    }
}

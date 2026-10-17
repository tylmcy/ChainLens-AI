package io.chainlens.knowledge;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeSearchContractTest {

    @Test
    void noop明确返回关闭而不是伪装成空命中() {
        KnowledgeSearch.SearchResult result = new NoopKnowledgeSearch().search("无限授权是什么意思");

        assertThat(result.available()).isFalse();
        assertThat(result.status()).isEqualTo(KnowledgeSearch.Availability.DISABLED);
        assertThat(result.reason()).isEqualTo("知识库未启用");
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void 可用但无命中与不可用是两种状态() {
        KnowledgeSearch.SearchResult result = KnowledgeSearch.SearchResult.available(List.of());

        assertThat(result.available()).isTrue();
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void 不可用状态禁止携带命中() {
        KnowledgeSearch.KnowledgeHit hit = new KnowledgeSearch.KnowledgeHit(
                "说明", "docs/knowledge/rule.md", "规则", List.of("TX-FAILED"), 0.9);

        assertThatThrownBy(() -> new KnowledgeSearch.SearchResult(
                KnowledgeSearch.Availability.UNAVAILABLE, "数据库故障", List.of(hit)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

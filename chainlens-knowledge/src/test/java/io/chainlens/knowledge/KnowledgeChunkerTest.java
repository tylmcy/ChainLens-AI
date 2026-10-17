package io.chainlens.knowledge;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    private final KnowledgeChunker chunker = new KnowledgeChunker();

    @Test
    void 相同输入重复切块得到相同ID() {
        KnowledgeDocument document = document("## 含义\n\n" + "无限授权需要谨慎核对。".repeat(45));

        List<KnowledgeChunk> first = chunker.chunk(document);
        List<KnowledgeChunk> second = chunker.chunk(document);

        assertThat(first).isEqualTo(second);
        assertThat(first).allSatisfy(chunk -> {
            assertThat(chunk.id()).hasSize(64);
            assertThat(chunk.contentHash()).hasSize(64);
            assertThat(chunk.content().length()).isLessThanOrEqualTo(KnowledgeChunker.TARGET_MAX);
        });
    }

    @Test
    void 长中文段落优先按标点断开并保留不超过八十字重叠() {
        String sentence = "授权额度很大时，应先核对代币合约、spender 地址和业务目的。";
        List<KnowledgeChunk> chunks = chunker.chunk(document("## 核对方式\n\n" + sentence.repeat(40)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.heading()).isEqualTo("核对方式");
            assertThat(chunk.content().length()).isBetween(1, KnowledgeChunker.TARGET_MAX);
        });
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(commonBoundary(chunks.get(i - 1).content(), chunks.get(i).content()))
                    .isLessThanOrEqualTo(KnowledgeChunker.OVERLAP_MAX);
        }
    }

    @Test
    void 标题被每个片段继承且空白规范化() {
        KnowledgeDocument document = document("## 风险解释   \n\n第一段。   \n\n\n第二段。\n");

        List<KnowledgeChunk> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).heading()).isEqualTo("风险解释");
        assertThat(chunks.get(0).content()).isEqualTo("第一段。\n\n第二段。");
    }

    private static int commonBoundary(String left, String right) {
        int max = Math.min(Math.min(left.length(), right.length()), KnowledgeChunker.OVERLAP_MAX + 1);
        for (int length = max; length > 0; length--) {
            if (left.endsWith(right.substring(0, length))) {
                return length;
            }
        }
        return 0;
    }

    private static KnowledgeDocument document(String body) {
        return new KnowledgeDocument(
                "approval", "授权", "risk-rule", List.of("ERC20-UNLIMITED-APPROVAL"),
                "1.0", "docs/knowledge/approval.md", body);
    }
}

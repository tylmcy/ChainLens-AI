package io.chainlens.knowledge;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeDocumentParserTest {

    @TempDir
    Path root;

    private final KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

    @Test
    void 解析合法文档并保留可追溯来源() {
        Path file = root.resolve("docs/knowledge/approval.md");
        KnowledgeDocument document = parser.parse(root, file, valid("[ERC20-UNLIMITED-APPROVAL]", "正文内容"));

        assertThat(document.id()).isEqualTo("erc20-approval");
        assertThat(document.ruleIds()).containsExactly("ERC20-UNLIMITED-APPROVAL");
        assertThat(document.source()).isEqualTo("docs/knowledge/approval.md");
        assertThat(document.body()).isEqualTo("正文内容");
    }

    @Test
    void 拒绝缺字段与空正文() {
        String missing = valid("[TX-FAILED]", "正文").replace("category: risk-rule\n", "");
        assertThatThrownBy(() -> parser.parse(root, root.resolve("docs/knowledge/a.md"), missing))
                .hasMessageContaining("字段不完整");

        assertThatThrownBy(() -> parser.parse(root, root.resolve("docs/knowledge/a.md"), valid("[TX-FAILED]", "  ")))
                .hasMessageContaining("正文不能为空");
    }

    @Test
    void 拒绝未知规则和路径或Url注入() {
        assertThatThrownBy(() -> parser.parse(root, root.resolve("docs/knowledge/a.md"), valid("[MADE-UP]", "正文")))
                .hasMessageContaining("未知 ruleId");

        assertThatThrownBy(() -> parser.parse(root, root.resolve("../outside.md"), valid("[]", "正文")))
                .hasMessageContaining("受管目录");

        String urlTitle = valid("[]", "正文").replace("title: ERC-20 授权\n", "title: https://evil.example/x\n");
        assertThatThrownBy(() -> parser.parse(root, root.resolve("docs/knowledge/a.md"), urlTitle))
                .hasMessageContaining("路径/URL");
    }

    private static String valid(String ruleIds, String body) {
        return """
                ---
                id: erc20-approval
                title: ERC-20 授权
                category: risk-rule
                ruleIds: %s
                version: 1.0
                ---
                %s
                """.formatted(ruleIds, body);
    }
}

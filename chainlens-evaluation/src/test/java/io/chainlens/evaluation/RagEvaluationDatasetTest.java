package io.chainlens.evaluation;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagEvaluationDatasetTest {

    @Test
    void 数据集包含十二条版本化用例和两条无关查询() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/rag-eval-v1.json")) {
            assertThat(input).isNotNull();
            JsonNode root = new ObjectMapper().readTree(input);
            assertThat(root.path("version").asText()).isEqualTo("1.0");
            assertThat(root.path("cases")).hasSize(12);

            Set<String> ids = new HashSet<>();
            int unrelated = 0;
            for (JsonNode item : root.path("cases")) {
                assertThat(ids.add(item.path("id").asText())).isTrue();
                assertThat(item.path("query").asText()).isNotBlank();
                assertThat(item.path("expectedRuleIds").isArray()).isTrue();
                if (item.path("expectedSource").isNull()) {
                    unrelated++;
                    assertThat(item.path("expectedRuleIds")).isEmpty();
                } else {
                    assertThat(item.path("expectedSource").asText()).startsWith("docs/knowledge/");
                }
            }
            assertThat(unrelated).isEqualTo(2);
        }
    }
}

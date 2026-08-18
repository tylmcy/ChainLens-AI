package io.chainlens.knowledge;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0 技术风险探测（R2）：验证 Spring AI 2.0.0 本地 ONNX 嵌入（transformers）链路。
 *
 * <p>默认构建跳过本测试（@Tag smoke + 模型需联网下载一次，约 100~200MB，之后走本地缓存）。
 * 运行方式：
 * <pre>
 *   mvn -pl chainlens-knowledge test -Dsurefire.excludedGroups=none -Dtest=LocalEmbeddingSmokeTest
 * </pre>
 *
 * <p>模型与分词器 URI 可用系统属性覆盖（例如网络受限时使用可达镜像）：
 * {@code -Dmodel.uri=file:///path/model.onnx -Dtokenizer.uri=file:///path/tokenizer.json}
 *
 * <p>若 ONNX 在 macOS arm64 存在原生库问题（R2），按设计回退 Ollama（bge-m3）。
 */
@SpringBootTest(
        classes = LocalEmbeddingSmokeTest.TestConfig.class,
        properties = {
                "spring.ai.embedding.transformer.onnx.model-uri="
                        + "${model.uri:https://huggingface.co/BAAI/bge-small-zh-v1.5/resolve/main/onnx/model.onnx}",
                "spring.ai.embedding.transformer.tokenizer.uri="
                        + "${tokenizer.uri:https://huggingface.co/BAAI/bge-small-zh-v1.5/resolve/main/tokenizer.json}",
                "spring.ai.embedding.transformer.onnx.model-output-name=sentence_embedding"
        })
@Tag("smoke")
class LocalEmbeddingSmokeTest {

    @Autowired
    EmbeddingModel embeddingModel;

    @Test
    @DisplayName("BGE-small-zh 嵌入中文文本并输出 512 维向量")
    void embedsChineseText() {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(
                List.of("以太坊交易风险分析"), null));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getOutput()).hasSize(512);
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }
}

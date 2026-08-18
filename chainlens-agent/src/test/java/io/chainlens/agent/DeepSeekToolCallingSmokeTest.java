package io.chainlens.agent;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0 技术风险探测（R1）：验证 Spring AI 2.0.0 + DeepSeek 的工具调用链路。
 *
 * <p>运行条件：需要环境变量 DEEPSEEK_API_KEY（默认构建跳过本测试）：
 * <pre>
 *   export DEEPSEEK_API_KEY=sk-...
 *   mvn -pl chainlens-agent test -Dsurefire.excludedGroups=none -Dtest=DeepSeekToolCallingSmokeTest
 * </pre>
 *
 * <p>依据：社区已知 spring-ai#6118 报告 deepseek-v4-pro 在 Spring AI 下工具调用报错，
 * 本测试用官方推荐的 deepseek-chat 别名验证；若复现问题，按设计回退 OpenAI 兼容适配器。
 *
 * <p>注意：本测试类允许 import Provider 类（ArchUnit 的"业务代码不 import 厂商类"规则
 * 只约束 src/main，不约束 src/test）。
 */
@Tag("smoke")
class DeepSeekToolCallingSmokeTest {

    @Test
    @DisplayName("DeepSeek 通过 Spring AI 调用工具并引用工具返回结果")
    void toolCallingRoundTrip() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "未设置 DEEPSEEK_API_KEY，跳过 LLM 冒烟测试");

        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com"))
                .build();
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .build();
        ChatClient client = ChatClient.builder(model).build();

        String answer = client.prompt("请调用 getChainId 工具，然后用一句话告诉我当前的链 ID。")
                .tools(new SmokeTools())
                .call()
                .content();

        assertThat(answer).isNotBlank().contains("31337");
    }

    /** 冒烟专用工具：返回固定链 ID，验证工具调用能闭环。 */
    static class SmokeTools {

        @Tool(description = "返回当前连接的 Ethereum 链 ID")
        String getChainId() {
            return "31337";
        }
    }
}

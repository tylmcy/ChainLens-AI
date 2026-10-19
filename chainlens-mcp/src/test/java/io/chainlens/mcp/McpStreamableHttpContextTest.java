package io.chainlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MCP 协议兼容闸门：证明 Spring AI 2.0.1 在当前 Boot 4.1 / Java 21 构建中
 * 确实装配 Streamable HTTP provider，并由 Spring MVC 接管 /mcp。
 */
@SpringBootTest(
        classes = McpStreamableHttpContextTest.TestApplication.class,
        properties = {
                "spring.ai.mcp.server.protocol=STREAMABLE",
                "spring.ai.mcp.server.type=SYNC",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp",
                "spring.ai.model.chat=none",
                "spring.ai.model.embedding=none",
                // 最小协议上下文不需要数据库，避免 agent 的传递依赖触发 DataSource 自动配置。
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
        })
@AutoConfigureMockMvc
class McpStreamableHttpContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void streamableProvider和Mcp路由已注册() throws Exception {
        assertThat(context.getBeansOfType(WebMvcStreamableServerTransportProvider.class))
                .hasSize(1);

        // 空 POST 不是合法 MCP 帧，但只要不是 404，就证明 /mcp 已由协议路由接管。
        int status = mockMvc.perform(post("/mcp"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(404);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}

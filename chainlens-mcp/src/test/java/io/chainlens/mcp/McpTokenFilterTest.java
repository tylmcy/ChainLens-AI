package io.chainlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class McpTokenFilterTest {

    @Test
    void configuredTokenRejectsMissingAndWrongCredentialsBeforeEndpointRuns() throws Exception {
        CountingEndpoint endpoint = new CountingEndpoint();
        MockMvc mvc = mvc(endpoint, "correct-secret");

        mvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp").header("Authorization", "Bearer wrong-secret"))
                .andExpect(status().isUnauthorized());

        assertThat(endpoint.calls.get()).isZero();
    }

    @Test
    void configuredTokenAllowsCorrectBearerCredential() throws Exception {
        CountingEndpoint endpoint = new CountingEndpoint();

        mvc(endpoint, "correct-secret").perform(post("/mcp")
                        .header("Authorization", "Bearer correct-secret"))
                .andExpect(status().isNoContent());

        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    @Test
    void blankConfigurationDisablesAuthentication() throws Exception {
        CountingEndpoint endpoint = new CountingEndpoint();

        mvc(endpoint, " ").perform(post("/mcp"))
                .andExpect(status().isNoContent());

        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    private static MockMvc mvc(CountingEndpoint endpoint, String token) {
        return MockMvcBuilders.standaloneSetup(endpoint)
                .addFilters(new McpTokenFilter(token))
                .build();
    }

    @RestController
    private static final class CountingEndpoint {
        private final AtomicInteger calls = new AtomicInteger();

        @PostMapping("/mcp")
        ResponseEntity<Void> call() {
            calls.incrementAndGet();
            return ResponseEntity.noContent().build();
        }
    }
}

package io.chainlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.chainlens.chain.rpc.RpcException;
import io.chainlens.chain.rpc.RpcUnavailableException;
import io.chainlens.chain.rpc.TransactionNotFoundException;

class McpErrorMapperTest {

    @Test
    void mapsRpcFailuresToStableSafeCategories() {
        assertMapped(new TransactionNotFoundException("0x" + "a".repeat(64)),
                McpErrorCode.TRANSACTION_NOT_FOUND, false);
        assertMapped(new RpcUnavailableException("https://user:secret@rpc.example"),
                McpErrorCode.RPC_UNAVAILABLE, true);
        assertMapped(new RpcException("raw payload with API_KEY=secret", false),
                McpErrorCode.INTERNAL_ERROR, false);
        assertMapped(new IllegalStateException("database password=secret"),
                McpErrorCode.INTERNAL_ERROR, false);
    }

    @Test
    void knowledgePortExceptionUsesKnowledgeUnavailableWithoutLeakingCause() {
        ChainLensMcpTools tools = new ChainLensMcpTools(
                (hash, listener) -> { throw new UnsupportedOperationException(); },
                hash -> { throw new UnsupportedOperationException(); },
                new io.chainlens.rules.RuleEngine(List.of(new io.chainlens.rules.rule.FailedTransactionRule())),
                selector -> List.of(),
                query -> { throw new IllegalStateException("jdbc:postgresql://user:secret@host/db"); },
                new java.util.concurrent.Semaphore(1), null, "streamable-http");

        assertThatThrownBy(() -> tools.searchKnowledge("无限授权"))
                .isInstanceOfSatisfying(ChainLensMcpException.class, error -> {
                    assertThat(error.code()).isEqualTo(McpErrorCode.KNOWLEDGE_UNAVAILABLE);
                    assertThat(error.retryable()).isTrue();
                    assertThat(error.getMessage()).doesNotContain("jdbc", "secret", "IllegalStateException");
                });
    }

    private static void assertMapped(RuntimeException source, McpErrorCode code, boolean retryable) {
        ChainLensMcpException mapped = McpErrorMapper.map(source);
        assertThat(mapped.code()).isEqualTo(code);
        assertThat(mapped.retryable()).isEqualTo(retryable);
        assertThat(mapped.traceId()).isNotBlank();
        assertThat(mapped.getMessage()).doesNotContain("secret", "API_KEY", "https://");
    }
}

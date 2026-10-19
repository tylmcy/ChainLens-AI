package io.chainlens.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 未知异常不能把模型或数据库返回的敏感文本原样送给 REST/SSE 客户端。 */
class ErrorMapperSecretTest {

    @Test
    void genericErrorResponseDoesNotEchoSecretBearingMessage() {
        ErrorResponse response = ErrorMapper.map(new IllegalStateException(
                "provider request failed: api_key=SECRET_SENTINEL"));

        assertThat(response.code()).isEqualTo(ErrorMapper.CODE_INTERNAL_ERROR);
        assertThat(response.message()).doesNotContain("SECRET_SENTINEL", "api_key");
    }
}

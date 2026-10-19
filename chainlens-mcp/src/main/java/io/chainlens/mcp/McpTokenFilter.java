package io.chainlens.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MCP 端点的可选 Bearer Token 门禁。
 *
 * <p>Token 未配置时不启用认证；配置后在 MCP 协议解析和工具执行之前拒绝。
 * 比较使用 JDK 常量时间原语，且错误响应不回显任何 Token 内容。
 */
public final class McpTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final byte[] expectedToken;

    public McpTokenFilter(String configuredToken) {
        this.expectedToken = configuredToken == null || configuredToken.isBlank()
                ? null
                : configuredToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (expectedToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        byte[] presented = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (!MessageDigest.isEqual(expectedToken, presented)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"MCP Bearer Token 缺失或错误\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

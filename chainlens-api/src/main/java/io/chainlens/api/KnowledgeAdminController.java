package io.chainlens.api;

import java.net.InetAddress;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.chainlens.knowledge.KnowledgeIngestionService;
import io.chainlens.knowledge.KnowledgeReadiness;
import io.chainlens.knowledge.KnowledgeSyncStats;

/** 本机教学与排错用同步端点；默认不注册，也不接受目录或 URL 参数。 */
@RestController
@RequestMapping("/api/v1/knowledge")
@ConditionalOnProperty(name = "chainlens.knowledge.admin-enabled", havingValue = "true")
@ConditionalOnExpression("'${chainlens.knowledge.enabled:false}' == 'true' "
        + "&& '${spring.ai.model.embedding:none}' == 'transformers'")
public class KnowledgeAdminController {

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeReadiness readiness;

    public KnowledgeAdminController(KnowledgeIngestionService ingestionService, KnowledgeReadiness readiness) {
        this.ingestionService = ingestionService;
        this.readiness = readiness;
    }

    @PostMapping("/sync")
    public KnowledgeSyncStats synchronize(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "知识同步只允许从本机 loopback 调用");
        }
        readiness.syncing();
        try {
            KnowledgeSyncStats stats = ingestionService.synchronize();
            readiness.ready();
            return stats;
        } catch (RuntimeException e) {
            readiness.failed("手动同步失败");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "知识同步失败", e);
        }
    }

    private static boolean isLoopback(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}

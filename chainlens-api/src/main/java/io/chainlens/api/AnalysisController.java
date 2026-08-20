package io.chainlens.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.chainlens.model.AnalysisResult;

/**
 * 分析 API（设计文档 3.2）：
 * <ul>
 *   <li>POST 默认同步返回完整 JSON（curl 友好）；</li>
 *   <li>POST + Accept: text/event-stream → SSE 直播进度；</li>
 *   <li>GET /{id} 查历史；GET /{id}/stream 重放时间线。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/analyses")
@Tag(name = "analyses", description = "Ethereum 交易分析：取证、规则与报告")
public class AnalysisController {

    private final AnalysisApplicationService service;
    private final AnalysisRepository repository;

    public AnalysisController(AnalysisApplicationService service, AnalysisRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "分析一笔交易（同步返回完整结果）")
    public AnalysisResult analyzeJson(@Valid @RequestBody AnalysisRequest request) {
        return service.analyze(resolveHash(request));
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "分析一笔交易（SSE 直播进度：stage.started → rules.completed → report.completed → analysis.completed）")
    public SseEmitter analyzeStream(@Valid @RequestBody AnalysisRequest request) {
        return service.startStreaming(resolveHash(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询历史分析")
    public AnalysisResult getById(@PathVariable String id) {
        return repository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "从历史重放分析时间线")
    public SseEmitter replay(@PathVariable String id) {
        return service.replay(id);
    }

    /** 阶段 0 归一化：txHash 字段优先，否则从 question 纯正则提取（设计文档 3.1）。 */
    private static String resolveHash(AnalysisRequest request) {
        String hash = (request.txHash() != null && !request.txHash().isBlank())
                ? request.txHash().trim()
                : HashExtractor.extract(request.question());
        if (hash == null) {
            throw new InvalidInputException("无法从输入中提取交易哈希：请提供 txHash，或在 question 中包含 0x+64 位十六进制哈希");
        }
        if (!hash.matches("0x[0-9a-fA-F]{64}")) {
            throw new InvalidInputException("交易哈希格式非法（应为 0x + 64 位十六进制）");
        }
        return hash;
    }
}

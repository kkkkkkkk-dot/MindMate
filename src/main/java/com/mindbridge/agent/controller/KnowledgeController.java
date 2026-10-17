package com.mindbridge.agent.controller;

import com.mindbridge.agent.dto.KnowledgeIngestRequest;
import com.mindbridge.agent.dto.KnowledgeIngestResponse;
import com.mindbridge.agent.service.knowledge.KnowledgeFileService;
import com.mindbridge.agent.service.knowledge.KnowledgeService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/admin/knowledge")
/**
 * 管理员知识库维护接口。
 *
 * <p>支持直接写入文本，也支持上传 PDF、Markdown、txt 文件作为 RAG 知识库来源。</p>
 */
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeFileService knowledgeFileService;

    public KnowledgeController(KnowledgeService knowledgeService, KnowledgeFileService knowledgeFileService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeFileService = knowledgeFileService;
    }

    @PostMapping
    public KnowledgeIngestResponse ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        // JSON 接口适合脚本或调试时直接写入一段知识。
        int chunks = knowledgeService.ingest(request.source(), request.content());
        return new KnowledgeIngestResponse(request.source(), chunks);
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<KnowledgeIngestResponse> ingestFile(@RequestPart("file") FilePart file) {
        long declaredLength = file.headers().getContentLength();
        if (declaredLength > KnowledgeFileService.MAX_FILE_BYTES) {
            return Mono.error(new IllegalArgumentException("文件不能超过 10MB"));
        }

        // 上传内容流式写入临时文件，避免把大文件整体放进堆内存。
        return Mono.fromCallable(() -> Files.createTempFile("mindbridge-upload-", ".tmp"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(tempFile -> writeToTempFile(file, tempFile)
                        .then(Mono.fromCallable(() -> {
                            int chunks = knowledgeFileService.ingest(file.filename(), tempFile);
                            return new KnowledgeIngestResponse(file.filename(), chunks);
                        }).subscribeOn(Schedulers.boundedElastic()))
                        .doFinally(signal -> deleteQuietly(tempFile)));
    }

    private Mono<Void> writeToTempFile(FilePart file, Path tempFile) {
        AtomicLong totalBytes = new AtomicLong();
        Flux<DataBuffer> limitedContent = file.content().doOnNext(buffer -> {
            long total = totalBytes.addAndGet(buffer.readableByteCount());
            if (total > KnowledgeFileService.MAX_FILE_BYTES) {
                DataBufferUtils.release(buffer);
                throw new IllegalArgumentException("文件不能超过 10MB");
            }
        });
        return DataBufferUtils.write(limitedContent, tempFile);
    }

    private void deleteQuietly(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // 临时文件清理失败不应覆盖已经完成的业务响应。
        }
    }
}
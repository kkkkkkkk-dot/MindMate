package com.mindbridge.agent.service.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
/**
 * 管理员文件上传知识库服务。
 *
 * <p>负责文件大小校验、类型识别和文本抽取，抽取后的文本交给 KnowledgeService 处理。</p>
 */
public class KnowledgeFileService {

    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final KnowledgeService knowledgeService;

    public KnowledgeFileService(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public int ingest(String filename, Path file) {
        try {
            long size = Files.size(file);
            if (size == 0) {
                throw new IllegalArgumentException("文件内容为空");
            }
            if (size > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("文件不能超过 10MB");
            }
            String source = sanitizeSource(filename);
            String text = extractText(source, file);
            if (text.isBlank()) {
                throw new IllegalArgumentException("没有从文件中解析出可用文本");
            }
            return knowledgeService.ingest(source, text);
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件读取失败：" + exception.getMessage(), exception);
        }
    }

    private String extractText(String filename, Path file) throws IOException {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return extractPdf(file);
        }
        // Markdown 和 txt 都按 UTF-8 文本处理，适合管理员维护轻量知识库。
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("仅支持 PDF、Markdown 和 txt 文件");
    }

    private String extractPdf(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (Exception exception) {
            throw new IllegalArgumentException("PDF 文本解析失败：" + exception.getMessage(), exception);
        }
    }

    private String sanitizeSource(String filename) {
        String source = filename == null || filename.isBlank() ? "uploaded-knowledge" : filename.trim();
        // source 会进入数据库和后台列表，去掉路径分隔符避免显示本地路径。
        source = source.replaceAll("[\\\\/]+", "-");
        return source.length() > 180 ? source.substring(source.length() - 180) : source;
    }
}
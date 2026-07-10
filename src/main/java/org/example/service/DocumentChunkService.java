package org.example.service;

import org.example.config.DocumentChunkConfig;
import org.example.domain.po.DocumentChunk;
import org.example.domain.po.DocumentSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分片服务。
 *
 * <p>将长文档切分为多个语义上相对完整的片段，优先按 Markdown 标题分割，然后按段落边界划分，
 * 并在分片之间保留重叠内容以便在检索或拼接时保留上下文连续性。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li>标题优先：保留章节语义边界，便于检索返回更具结构化的引用</li>
 *   <li>段落分割：避免在句子中间进行切分，提升片段语义完整性</li>
 *   <li>重叠策略：通过 overlap 保留上下文，改善跨片段检索时的上下文连贯性</li>
 * </ul>
 * </p>
 */
@Service
public class DocumentChunkService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkService.class);

    /**
     * 分片配置（最大分片大小、重叠长度等），通过配置类注入。
     */
    @Autowired
    private DocumentChunkConfig chunkConfig;

    /**
     * 将文档内容切分为若干 DocumentChunk 列表。
     *
     * @param content  文档全文内容
     * @param filePath 文件路径，仅用于日志及构建元数据
     * @return 文档分片列表，顺序与原文相同
     */
    public List<DocumentChunk> chunkDocument(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            logger.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        // 1. 首先尝试按标题分割（Markdown格式）
        List<DocumentSection> sections = splitByHeadings(content);
        
        // 2. 对每个章节进行进一步分片
        int globalChunkIndex = 0;
        for (DocumentSection section : sections) {
            List<DocumentChunk> sectionChunks = chunkSection(section, globalChunkIndex);
            chunks.addAll(sectionChunks);
            globalChunkIndex += sectionChunks.size();
        }

        logger.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());
        return chunks;
    }

    /**
     * 按照 Markdown 标题分割文档。
     *
     * <p>正则匹配 #、##、### 等标题，并将标题上下文块作为独立章节返回；若未匹配到标题则整体作为单一章节。</p>
     */
    private List<DocumentSection> splitByHeadings(String content) {
        List<DocumentSection> sections = new ArrayList<>();
        
        // 匹配 Markdown 标题：# 标题, ## 标题, ### 标题等
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);

        int lastEnd = 0;
        String currentTitle = null;

        while (matcher.find()) {
            // 保存上一个章节
            if (lastEnd < matcher.start()) {
                String sectionContent = content.substring(lastEnd, matcher.start()).trim();
                if (!sectionContent.isEmpty()) {
                    sections.add(new DocumentSection(currentTitle, sectionContent, lastEnd));
                }
            }

            // 更新当前标题
            currentTitle = matcher.group(2).trim();
            lastEnd = matcher.start();
        }

        // 添加最后一个章节
        if (lastEnd < content.length()) {
            String sectionContent = content.substring(lastEnd).trim();
            if (!sectionContent.isEmpty()) {
                sections.add(new DocumentSection(currentTitle, sectionContent, lastEnd));
            }
        }

        // 如果没有找到任何标题，将整个文档作为一个章节
        if (sections.isEmpty()) {
            sections.add(new DocumentSection(null, content, 0));
        }

        return sections;
    }

    /**
     * 对单个章节进行分片。
     *
     * <p>若章节长度小于配置的最大分片尺寸，则作为单个分片；否则按段落分割并在分片之间保留 overlap 以保证片段间上下文连贯性。</p>
     */
    private List<DocumentChunk> chunkSection(DocumentSection section, int startChunkIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = section.getContent();
        String title = section.getTitle();

        // 如果章节内容小于最大尺寸，直接作为一个分片
        if (content.length() <= chunkConfig.getMaxSize()) {
            DocumentChunk chunk = new DocumentChunk(
                content, 
                section.getStartIndex(), 
                section.getStartIndex() + content.length(), 
                startChunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
            return chunks;
        }

        // 章节内容较长，需要进一步分片
        // 优先在段落边界分割
        List<String> paragraphs = splitByParagraphs(content);
        
        StringBuilder currentChunk = new StringBuilder();
        int currentStartIndex = section.getStartIndex();
        int chunkIndex = startChunkIndex;

        for (String paragraph : paragraphs) {
            // 如果当前分片加上新段落超过最大尺寸
            if (currentChunk.length() > 0 && 
                currentChunk.length() + paragraph.length() > chunkConfig.getMaxSize()) {
                
                // 保存当前分片
                String chunkContent = currentChunk.toString().trim();
                DocumentChunk chunk = new DocumentChunk(
                    chunkContent,
                    currentStartIndex,
                    currentStartIndex + chunkContent.length(),
                    chunkIndex++
                );
                chunk.setTitle(title);
                chunks.add(chunk);

                // 开始新分片，包含重叠部分
                String overlap = getOverlapText(chunkContent);
                currentChunk = new StringBuilder(overlap);
                currentStartIndex = currentStartIndex + chunkContent.length() - overlap.length();
            }

            currentChunk.append(paragraph).append("\n\n");
        }

        // 保存最后一个分片
        if (currentChunk.length() > 0) {
            String chunkContent = currentChunk.toString().trim();
            DocumentChunk chunk = new DocumentChunk(
                chunkContent,
                currentStartIndex,
                currentStartIndex + chunkContent.length(),
                chunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * 按段落分割文本（以双换行作为段落分隔符）。
     */
    private List<String> splitByParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        
        // 按双换行符分割段落
        String[] parts = content.split("\n\n+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        return paragraphs;
    }

    /**
     * 获取重叠文本，从文本末尾提取指定长度用于下一个分片的开头。
     *
     * <p>为了提升上下文完整性，优先在句子结尾处截断（例如中文句号、问号、感叹号），以避免断句导致语义不连贯。</p>
     */
    private String getOverlapText(String text) {
        int overlapSize = Math.min(chunkConfig.getOverlap(), text.length());
        if (overlapSize <= 0) {
            return "";
        }

        // 从末尾提取重叠内容
        String overlap = text.substring(text.length() - overlapSize);
        
        // 尝试在句子边界截断（查找最后一个句号、问号、感叹号）
        int lastSentenceEnd = Math.max(
            overlap.lastIndexOf('。'),
            Math.max(overlap.lastIndexOf('？'), overlap.lastIndexOf('！'))
        );
        
        if (lastSentenceEnd > overlapSize / 2) {
            return overlap.substring(lastSentenceEnd + 1).trim();
        }

        return overlap.trim();
    }

    /**
     * 章节数据类
     */
}

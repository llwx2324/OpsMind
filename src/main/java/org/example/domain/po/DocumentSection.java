package org.example.domain.po;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档章节对象。
 *
 * <p>用于文档分片阶段表示按标题或自然边界切分出的章节，包含章节标题、正文和原文起始位置。</p>
 */
@Getter
@AllArgsConstructor
public class DocumentSection {

    /**
     * 章节标题，若文档未使用标题结构则可能为 null。
     */
    private String title;

    /**
     * 章节正文内容。
     */
    private String content;

    /**
     * 章节在原始文档中的起始位置。
     */
    private int startIndex;
}

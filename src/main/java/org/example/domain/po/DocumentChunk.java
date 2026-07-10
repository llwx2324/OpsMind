package org.example.domain.po;

import lombok.Getter;
import lombok.Setter;

/**
 * 文档分片对象。
 *
 * <p>表示原始文档中的一个连续片段，包含内容、起止位置、分片序号与标题信息。
 * 该对象通常由分片服务生成，再由向量化入库服务转换为向量并写入 Milvus。</p>
 */
@Getter
@Setter
public class DocumentChunk {

    /**
     * 分片正文内容。
     */
    private String content;

    /**
     * 分片在原文中的起始位置。
     */
    private int startIndex;

    /**
     * 分片在原文中的结束位置。
     */
    private int endIndex;

    /**
     * 分片编号，用于排序和去重。
     */
    private int chunkIndex;

    /**
     * 分片标题或章节标题。
     */
    private String title;

    /**
     * 无参构造函数。
     */
    public DocumentChunk() {
    }

    /**
     * 创建分片对象。
     *
     * @param content 分片内容
     * @param startIndex 起始位置
     * @param endIndex 结束位置
     * @param chunkIndex 分片序号
     */
    public DocumentChunk(String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }

    /**
     * 返回便于日志查看的摘要字符串。
     */
    @Override
    public String toString() {
        return "DocumentChunk{" +
                "chunkIndex=" + chunkIndex +
                ", title='" + title + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                '}';
    }
}

package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 向量检索结果对象。
 *
 * <p>用于承载 Milvus 向量搜索返回的单条结果，供 RAG 检索、日志检索以及前端展示使用。</p>
 */
@Getter
@Setter
public class SearchResult {

    /**
     * 文档 ID。
     */
    private String id;

    /**
     * 文档内容。
     */
    private String content;

    /**
     * 相似度分数。
     */
    private float score;

    /**
     * 文档元数据。
     */
    private String metadata;
}

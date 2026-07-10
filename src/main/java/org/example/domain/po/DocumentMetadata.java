package org.example.domain.po;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档元数据。
 *
 * <p>该对象用于在文档向量化后随向量一起写入 Milvus，保存原始文件来源、扩展名、文件名以及分片信息。
 * 字段名通过 SerializedName 映射为 Milvus 中的 JSON 键，确保查询表达式与存储结构一致。</p>
 */
@Getter
@Setter
public class DocumentMetadata {

    /**
     * 原始文件路径或来源标识，对应 Milvus JSON 字段 _source。
     */
    @SerializedName("_source")
    private String source;

    /**
     * 文件扩展名，对应 Milvus JSON 字段 _extension。
     */
    @SerializedName("_extension")
    private String extension;

    /**
     * 文件名，对应 Milvus JSON 字段 _file_name。
     */
    @SerializedName("_file_name")
    private String fileName;

    /**
     * 分片序号，用于同一文件内分片排序与去重。
     */
    private int chunkIndex;

    /**
     * 文件总分片数。
     */
    private int totalChunks;

    /**
     * 分片标题，通常来自 Markdown 标题或章节标题。
     */
    private String title;
}

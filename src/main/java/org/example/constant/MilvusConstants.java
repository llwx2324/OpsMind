package org.example.constant;

/**
 * Milvus 相关常量。
 *
 * <p>统一维护 Milvus 连接与 collection schema 中使用的关键常量，避免在服务、工厂和工具类中散落硬编码值。</p>
 */
public class MilvusConstants {
    
    /**
     * Milvus 数据库名称。
     */
    public static final String MILVUS_DB_NAME = "default";
    
    /**
     * Milvus 集合名称。
     */
    public static final String MILVUS_COLLECTION_NAME = "biz";
    
    /**
     * 向量维度（embedding 模型输出维度）。
     */
    public static final int VECTOR_DIM = 1024;

    /**
     * ID 字段最大长度。
     */
    public static final int ID_MAX_LENGTH = 256;
    
    /**
     * Content 字段最大长度。
     */
    public static final int CONTENT_MAX_LENGTH = 8192;
    
    /**
     * 默认分片数。
     */
    public static final int DEFAULT_SHARD_NUMBER = 2;
    
    private MilvusConstants() {
        // 工具类，禁止实例化。
    }
}

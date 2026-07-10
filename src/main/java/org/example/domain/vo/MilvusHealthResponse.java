package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Milvus 健康状态响应对象。
 *
 * <p>用于健康检查接口返回当前 Milvus 连接状态及可访问 collection 列表，便于运维监控与联调验证。</p>
 */
@Getter
@Setter
public class MilvusHealthResponse {

    /**
     * 响应消息，通常用于说明当前健康状态。
     */
    private String message;

    /**
     * 当前可访问的 collection 列表。
     */
    private List<String> collections;

    /**
     * 错误信息，健康检查失败时可能非空。
     */
    private String error;
}

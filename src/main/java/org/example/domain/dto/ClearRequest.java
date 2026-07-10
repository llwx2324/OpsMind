package org.example.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 清空会话历史请求 DTO。
 *
 * <p>用于请求服务端清除指定会话的上下文历史，不会删除会话实体本身。</p>
 */
@Getter
@Setter
public class ClearRequest {

    /**
     * 待清空历史的会话 ID。
     */
    @JsonProperty("Id")
    @JsonAlias({"id", "ID"})
    private String id;
}

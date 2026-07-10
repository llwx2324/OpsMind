package org.example.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 聊天请求 DTO。
 *
 * <p>用于前端发起普通对话或流式对话请求，包含会话 ID 与用户问题。
 * 通过 JsonProperty / JsonAlias 兼容多种入参字段命名，降低前后端字段对齐成本。</p>
 */
public class ChatRequest {

    /**
     * 会话 ID。为空时通常由服务端自动创建新会话。
     */
    @JsonProperty("Id")
    @JsonAlias({"id", "ID"})
    private String id;

    /**
     * 用户输入的问题文本。
     */
    @JsonProperty("Question")
    @JsonAlias({"question", "QUESTION"})
    private String question;
}

package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 聊天响应对象。
 *
 * <p>用于封装普通对话或流式对话结束后的最终回答，包含成功标识、回答内容和错误信息。</p>
 */
@Getter
@Setter
public class ChatResponse {

    /**
     * 是否成功。
     */
    private boolean success;

    /**
     * 模型回答内容。
     */
    private String answer;

    /**
     * 错误信息，仅在失败场景下使用。
     */
    private String errorMessage;

    /**
     * 创建成功响应。
     *
     * @param answer 模型回答
     * @return 成功响应对象
     */
    public static ChatResponse success(String answer) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        return response;
    }

    /**
     * 创建失败响应。
     *
     * @param errorMessage 错误信息
     * @return 失败响应对象
     */
    public static ChatResponse error(String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}

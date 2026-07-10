package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * SSE 消息对象。
 *
 * <p>用于统一封装服务器发送事件中的消息类型和数据内容，方便前端按 content / error / done 三种事件处理。</p>
 */
@Getter
@Setter
public class SseMessage {

    /**
     * 消息类型，例如 content、error、done。
     */
    private String type;

    /**
     * 消息数据。
     */
    private String data;

    /**
     * 创建内容消息。
     *
     * @param data 消息内容
     * @return SSE 内容消息
     */
    public static SseMessage content(String data) {
        SseMessage message = new SseMessage();
        message.setType("content");
        message.setData(data);
        return message;
    }

    /**
     * 创建错误消息。
     *
     * @param errorMessage 错误信息
     * @return SSE 错误消息
     */
    public static SseMessage error(String errorMessage) {
        SseMessage message = new SseMessage();
        message.setType("error");
        message.setData(errorMessage);
        return message;
    }

    /**
     * 创建结束标记消息。
     *
     * @return SSE 结束消息
     */
    public static SseMessage done() {
        SseMessage message = new SseMessage();
        message.setType("done");
        message.setData(null);
        return message;
    }
}

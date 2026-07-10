package org.example.domain.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
/**
 * 聊天消息对象。
 *
 * <p>用于保存对话中的单条消息，包括角色（user/assistant）与消息内容。
 * 该对象既用于会话历史存储，也用于构造模型上下文输入。</p>
 */
public class ChatMessage {

    /**
     * 消息角色，通常为 user 或 assistant。
     */
    private String role;

    /**
     * 消息文本内容。
     */
    private String content;

    /**
     * 创建一条消息。
     *
     * @param role 消息角色
     * @param content 消息内容
     */
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 复制构造函数，用于生成消息的安全副本。
     *
     * @param source 源消息
     */
    public ChatMessage(ChatMessage source) {
        this(source.getRole(), source.getContent());
    }

    /**
     * 创建用户消息。
     *
     * @param content 用户输入内容
     * @return user 角色消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * 创建助手消息。
     *
     * @param content 助手回复内容
     * @return assistant 角色消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}

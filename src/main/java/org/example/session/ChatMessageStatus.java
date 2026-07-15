package org.example.session;

/**
 * 聊天消息的持久化状态。
 *
 * <p>用户消息在写入时内容已完整，当前固定为 {@code COMPLETED}；
 * 助手消息会经历生成中的 {@code PENDING}，再进入
 * {@code COMPLETED}、{@code CANCELLED} 或 {@code FAILED} 终态。</p>
 */
public enum ChatMessageStatus {
    /** 已创建，模型回答仍在生成；仅用于助手消息。 */
    PENDING,

    /** 内容完整，可参与后续上下文；用户消息创建后即为此状态。 */
    COMPLETED,

    /** 助手生成被客户端断开或主动取消，不参与后续上下文。 */
    CANCELLED,

    /** 助手模型调用或持久化失败，不参与后续上下文。 */
    FAILED
}

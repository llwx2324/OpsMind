package org.example.domain.vo;

import java.time.Instant;

/**
 * 会话消息分页接口的只读视图。
 *
 * @param sequence 会话内消息序号
 * @param role 消息角色
 * @param content 已持久化的消息内容
 * @param status 消息生命周期状态
 * @param createdAt 创建时间
 */
public record PersistentChatMessageView(long sequence, String role, String content, String status, Instant createdAt) {}

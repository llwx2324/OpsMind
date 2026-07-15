package org.example.domain.vo;

import java.time.Instant;

/**
 * 返回给当前用户的会话摘要视图。
 *
 * @param id 会话 ID
 * @param title 会话标题
 * @param createdAt 创建时间
 * @param updatedAt 最近更新时间，用于游标排序
 * @param expiresAt 数据过期时间
 */
public record ChatSessionView(String id, String title, Instant createdAt, Instant updatedAt, Instant expiresAt) {}

package org.example.domain.vo;

import java.time.Instant;

/**
 * 返回给记忆创建者的长期记忆视图。
 *
 * @param id 记忆 ID
 * @param type 记忆类型
 * @param content 已脱敏的记忆内容
 * @param expiresAt 记忆过期时间
 */
public record MemoryView(String id, String type, String content, Instant expiresAt) {}

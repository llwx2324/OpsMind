package org.example.security;

/**
 * 已通过认证的调用者身份。
 *
 * @param tenantId 租户唯一标识，所有会话与记忆查询的第一层隔离条件
 * @param userId 用户唯一标识，保证同一租户内的私人数据仍相互隔离
 */
public record CallerIdentity(String tenantId, String userId) {}

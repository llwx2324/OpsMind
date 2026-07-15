package org.example.session;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 会话持久化访问层，面向用户的查询必须同时附带 tenantId 与 userId。 */
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
    /** 按会话 ID 及完整调用者身份查询，越权会话与不存在会话返回相同结果。 */
    Optional<ChatSessionEntity> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);
    List<ChatSessionEntity> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId, Pageable pageable);
    /**
     * 使用更新时间和 ID 组成稳定游标，按倒序查询当前用户的会话。
     * 更新时间相同时以 ID 继续排序，避免翻页时重复或遗漏记录。
     */
    @Query("select s from ChatSessionEntity s where s.tenantId=:tenantId and s.userId=:userId and " +
            "(:beforeUpdatedAt is null or s.updatedAt < :beforeUpdatedAt or (s.updatedAt = :beforeUpdatedAt and s.id < :beforeId)) " +
            "order by s.updatedAt desc, s.id desc")
    List<ChatSessionEntity> findOwnerPage(String tenantId, String userId, Instant beforeUpdatedAt, String beforeId, Pageable pageable);
    long deleteByExpiresAtBefore(Instant time);
}

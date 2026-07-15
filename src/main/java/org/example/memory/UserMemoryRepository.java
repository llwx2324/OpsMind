package org.example.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 长期记忆持久化访问层，所有用户操作均以 tenantId 与 userId 强隔离。 */
public interface UserMemoryRepository extends JpaRepository<UserMemoryEntity, String> {
    List<UserMemoryEntity> findByTenantIdAndUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(String tenantId, String userId, String status, Instant now, Pageable pageable);
    Optional<UserMemoryEntity> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);
    Optional<UserMemoryEntity> findByTenantIdAndUserIdAndContentHash(String tenantId, String userId, String hash);
    long deleteByExpiresAtBefore(Instant now);
}

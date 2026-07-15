package org.example.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

/** 已删除记忆墓碑访问层，用于阻止同一脱敏内容被重新提取。 */
public interface MemoryTombstoneRepository extends JpaRepository<MemoryTombstoneEntity, Long> {
    boolean existsByTenantIdAndUserIdAndContentHashAndExpiresAtAfter(String tenantId, String userId, String hash, Instant now);
    long deleteByExpiresAtBefore(Instant now);
}

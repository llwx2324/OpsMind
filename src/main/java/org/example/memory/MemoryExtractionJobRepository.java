package org.example.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 长期记忆提取任务持久化访问层，支持失败重试和服务重启补偿。 */
public interface MemoryExtractionJobRepository extends JpaRepository<MemoryExtractionJobEntity, Long> {
    List<MemoryExtractionJobEntity> findByStatusInAndAttemptsLessThanOrderByCreatedAtAsc(List<String> statuses, int attempts, Pageable pageable);
    void deleteBySessionId(String sessionId);

    /**
     * 以条件更新原子认领任务并递增尝试次数。
     * 只有待处理或失败且未超过重试上限的任务能够认领成功。
     */
    @Modifying
    @Transactional
    @Query("update MemoryExtractionJobEntity j set j.status='RUNNING', j.attempts=j.attempts+1, j.updatedAt=CURRENT_TIMESTAMP where j.id=:id and j.status in ('PENDING','FAILED') and j.attempts < 3")
    int claim(Long id);
}

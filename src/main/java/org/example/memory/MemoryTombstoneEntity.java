package org.example.memory;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "memory_tombstone")
/** 用户删除记忆后写入的墓碑，阻止相同内容被后续提取任务自动写回。 */
public class MemoryTombstoneEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "user_id", nullable = false) private String userId;
    /** 被删除内容的哈希，与新提取结果比较。 */
    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)") private String contentHash;
    /** 原始来源会话，辅助限定墓碑影响范围。 */
    @Column(name = "source_session_id", columnDefinition = "CHAR(36)") private String sourceSessionId;
    @Column(name = "source_from_sequence") private Long sourceFromSequence;
    @Column(name = "source_to_sequence") private Long sourceToSequence;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected MemoryTombstoneEntity() {}
    public MemoryTombstoneEntity(String tenantId, String userId, String hash, Instant now, Instant expiresAt) { this.tenantId=tenantId; this.userId=userId; this.contentHash=hash; this.createdAt=now; this.expiresAt=expiresAt; }
}

package org.example.memory;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_memory")
/** 按租户和用户隔离的脱敏长期记忆。 */
public class UserMemoryEntity {
    @Id @Column(columnDefinition = "CHAR(36)") private String id;
    /** 记忆所属租户，与 userId 共同构成召回边界。 */
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    /** 记忆创建者，同租户其他用户默认不可见。 */
    @Column(name = "user_id", nullable = false) private String userId;
    /** 受控记忆类型，仅允许用户偏好或脱敏运维事实。 */
    @Column(name = "memory_type", nullable = false) private String memoryType;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    /** 脱敏内容哈希，用于去重并与 tombstone 匹配。 */
    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)") private String contentHash;
    /** 产生该记忆的来源会话。 */
    @Column(name = "source_session_id", columnDefinition = "CHAR(36)") private String sourceSessionId;
    /** 来源消息范围起始序号。 */
    @Column(name = "source_from_sequence") private Long sourceFromSequence;
    /** 来源消息范围结束序号。 */
    @Column(name = "source_to_sequence") private Long sourceToSequence;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    protected UserMemoryEntity() {}
    public UserMemoryEntity(String id, String tenantId, String userId, String type, String content, String hash, String sessionId, long from, long to, Instant now, Instant expiresAt) {
        this.id=id; this.tenantId=tenantId; this.userId=userId; this.memoryType=type; this.content=content; this.contentHash=hash; this.sourceSessionId=sessionId; this.sourceFromSequence=from; this.sourceToSequence=to; this.status="ACTIVE"; this.createdAt=now; this.expiresAt=expiresAt;
    }
    /** 记录记忆被成功召回的时间。 */
    public void markUsed(Instant now) { lastUsedAt = now; }
    /** 逻辑删除记忆，使其立即退出后续召回。 */
    public void delete() { status = "DELETED"; }
    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getMemoryType() { return memoryType; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public Instant getExpiresAt() { return expiresAt; }
}

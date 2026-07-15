-- 会话主表：tenant_id + user_id 是所有用户访问的强制隔离边界。
-- version 用于最终乐观锁校验；两个 through_sequence 字段分别记录摘要和记忆提取进度。
CREATE TABLE chat_session (
    id CHAR(36) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(256) NOT NULL,
    title VARCHAR(120) NULL,
    compact_summary MEDIUMTEXT NULL,
    compacted_through_sequence BIGINT NOT NULL DEFAULT 0,
    next_sequence BIGINT NOT NULL DEFAULT 0,
    memory_extracted_through_sequence BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_session_owner_updated (tenant_id, user_id, updated_at DESC, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 消息表：sequence_no 保证会话内顺序，request_id 保证单次用户提交幂等。
-- 非 COMPLETED 的助手消息会保留终态，但不会进入后续模型上下文。
CREATE TABLE chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id CHAR(36) NOT NULL,
    sequence_no BIGINT NOT NULL,
    request_id VARCHAR(80) NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
    CONSTRAINT uk_chat_message_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT uk_chat_message_request UNIQUE (session_id, request_id),
    INDEX idx_chat_message_session_status (session_id, status, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户长期记忆：内容已脱敏，并按租户、用户和内容哈希去重。
CREATE TABLE user_memory (
    id CHAR(36) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(256) NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    source_session_id CHAR(36) NULL,
    source_from_sequence BIGINT NULL,
    source_to_sequence BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    last_used_at DATETIME(3) NULL,
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_memory_hash UNIQUE (tenant_id, user_id, content_hash),
    INDEX idx_user_memory_owner_active (tenant_id, user_id, status, last_used_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 异步提取任务：唯一消息范围保证任务创建幂等，状态和 attempts 支持重启补偿。
CREATE TABLE memory_extraction_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id CHAR(36) NOT NULL,
    from_sequence BIGINT NOT NULL,
    to_sequence BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    model_version VARCHAR(128) NULL,
    result_hash CHAR(64) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_memory_extraction_range UNIQUE (session_id, from_sequence, to_sequence),
    CONSTRAINT fk_memory_extraction_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
    INDEX idx_memory_extraction_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 删除墓碑：用户删除记忆后，阻止相同内容在保留期内被自动提取回来。
CREATE TABLE memory_tombstone (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(256) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    source_session_id CHAR(36) NULL,
    source_from_sequence BIGINT NULL,
    source_to_sequence BIGINT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_memory_tombstone_owner_hash (tenant_id, user_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

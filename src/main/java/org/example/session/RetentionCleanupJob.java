package org.example.session;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/** 定时清理超过统一保留期的会话及其级联数据。 */
public class RetentionCleanupJob {
    private final ChatSessionService sessions;
    public RetentionCleanupJob(ChatSessionService sessions) { this.sessions = sessions; }
    @Scheduled(cron = "${opsmind.chat.cleanup-cron:0 15 3 * * *}")
    /** 按配置的 cron 触发物理过期清理。 */
    public void cleanup() { sessions.purgeExpired(); }
}

package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opsmind.chat")
/**
 * 会话、上下文压缩与长期记忆的统一配置。
 *
 * <p>token 数量当前通过字符数估算，相关参数用于控制上下文窗口、输出预留与安全余量。</p>
 */
public class ChatProperties {
    /** 会话、消息、摘要及记忆的统一保留天数。 */
    private int retentionDays = 90;
    /** 模型上下文窗口的估算 token 上限。 */
    private int contextWindowTokens = 32000;
    /** 为单次模型回答预留的最大 token 数。 */
    private int maxOutputTokens = 2000;
    /** 防止估算误差耗尽上下文窗口的安全余量。 */
    private int safetyMarginTokens = 3000;
    /** 压缩时始终保留原文的最近消息数量。 */
    private int keepRecentMessages = 12;
    /** 字符数换算为 token 数时使用的估算比例。 */
    private double charsPerToken = 3.5;
    /** 长期记忆子功能配置。 */
    private LongMemory longMemory = new LongMemory();

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public int getContextWindowTokens() { return contextWindowTokens; }
    public void setContextWindowTokens(int contextWindowTokens) { this.contextWindowTokens = contextWindowTokens; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public int getSafetyMarginTokens() { return safetyMarginTokens; }
    public void setSafetyMarginTokens(int safetyMarginTokens) { this.safetyMarginTokens = safetyMarginTokens; }
    public int getKeepRecentMessages() { return keepRecentMessages; }
    public void setKeepRecentMessages(int keepRecentMessages) { this.keepRecentMessages = keepRecentMessages; }
    public double getCharsPerToken() { return charsPerToken; }
    public void setCharsPerToken(double charsPerToken) { this.charsPerToken = charsPerToken; }
    public LongMemory getLongMemory() { return longMemory; }
    public void setLongMemory(LongMemory longMemory) { this.longMemory = longMemory; }

    /** 长期记忆提取与召回配置。 */
    public static class LongMemory {
        /** 是否启用长期记忆；默认关闭。 */
        private boolean enabled;
        /** 每完成多少个问答轮次创建一次提取任务。 */
        private int extractionIntervalPairs = 5;
        /** 记忆选择器超时时间；超时后跳过召回，不影响聊天。 */
        private long selectorTimeoutMs = 2000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getExtractionIntervalPairs() { return extractionIntervalPairs; }
        public void setExtractionIntervalPairs(int extractionIntervalPairs) { this.extractionIntervalPairs = extractionIntervalPairs; }
        public long getSelectorTimeoutMs() { return selectorTimeoutMs; }
        public void setSelectorTimeoutMs(long selectorTimeoutMs) { this.selectorTimeoutMs = selectorTimeoutMs; }
    }
}

package org.example.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;

@Service
/** Redis 会话上下文缓存；缓存不可用时调用方可从 MySQL 完整重建。 */
public class SessionContextCache {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    public SessionContextCache(StringRedisTemplate redis, ObjectMapper mapper) { this.redis = redis; this.mapper = mapper; }
    /**
     * 读取缓存；连接或反序列化失败时按未命中处理。
     *
     * @param sessionId 会话 ID
     * @return 可用的缓存上下文
     */
    public Optional<ChatContext> get(String sessionId) {
        try {
            String value = redis.opsForValue().get(key(sessionId));
            return value == null ? Optional.empty() : Optional.of(mapper.readValue(value, ChatContext.class));
        } catch (Exception ignored) { return Optional.empty(); }
    }
    /** 写入带固定 TTL 的派生上下文；写入失败不影响主业务。 */
    public void put(String sessionId, ChatContext context) {
        try { redis.opsForValue().set(key(sessionId), mapper.writeValueAsString(context), TTL); } catch (JsonProcessingException ignored) { }
    }
    /** 在消息、摘要或会话状态变化后主动使缓存失效。 */
    public void evict(String sessionId) { redis.delete(key(sessionId)); }
    private String key(String sessionId) { return "opsmind:chat:context:" + sessionId; }
}

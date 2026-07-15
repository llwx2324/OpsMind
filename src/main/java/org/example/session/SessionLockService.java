package org.example.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Service
/** 使用 Redis 租约串行化同一会话的模型调用与 SSE 生命周期。 */
public class SessionLockService {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final StringRedisTemplate redis;
    private final ScheduledExecutorService renewer = Executors.newScheduledThreadPool(1);
    /** Lua 比较删除，确保只能释放当前请求持有的锁。 */
    private final DefaultRedisScript<Long> compareDelete = new DefaultRedisScript<>("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    /** Lua 比较续期，防止旧持有者错误延长其他请求重新获取的锁。 */
    private final DefaultRedisScript<Long> compareExpire = new DefaultRedisScript<>("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end", Long.class);
    public SessionLockService(StringRedisTemplate redis) { this.redis = redis; }
    /**
     * 在有限等待时间内尝试获取会话锁，成功后周期性续租。
     *
     * @param sessionId 会话 ID
     * @return 锁句柄；超时或线程中断时返回 null
     */
    public LockHandle tryAcquire(String sessionId) {
        String key = "opsmind:chat:lock:" + sessionId;
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Boolean acquired;
        do {
            acquired = redis.opsForValue().setIfAbsent(key, token, LEASE);
            if (Boolean.TRUE.equals(acquired)) break;
            try { Thread.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        } while (System.nanoTime() < deadline);
        if (!Boolean.TRUE.equals(acquired)) return null;
        ScheduledFuture<?> future = renewer.scheduleAtFixedRate(() -> redis.execute(compareExpire, List.of(key), token, String.valueOf(LEASE.toMillis())), 10, 10, TimeUnit.SECONDS);
        return new LockHandle(key, token, future);
    }
    /** 自动取消续租并以持有者 token 安全释放锁的句柄。 */
    public final class LockHandle implements AutoCloseable {
        private final String key; private final String token; private final ScheduledFuture<?> renewal;
        private LockHandle(String key, String token, ScheduledFuture<?> renewal) { this.key=key; this.token=token; this.renewal=renewal; }
        /** 结束锁生命周期；重复或延迟释放不会删除其他请求持有的锁。 */
        @Override public void close() { renewal.cancel(false); redis.execute(compareDelete, List.of(key), token); }
    }
}

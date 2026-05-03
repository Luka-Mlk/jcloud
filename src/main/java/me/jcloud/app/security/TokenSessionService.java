package me.jcloud.app.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class TokenSessionService {
    private final StringRedisTemplate redisTemplate;
    private final long sessionTtl;

    public TokenSessionService(StringRedisTemplate redisTemplate,
                               @Value("${jcloud.session.ttl-seconds}") long sessionTtl) {
        this.redisTemplate = redisTemplate;
        this.sessionTtl = sessionTtl;
    }

    public long getSessionTtl() {
        return sessionTtl;
    }

    public void activateSession(String token, UUID userId, long ttlSeconds) {
        redisTemplate.opsForValue().set(token, userId.toString(), Duration.ofSeconds(ttlSeconds));
    }

    public boolean isSessionActive(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(token));
    }

    public void refreshSession(String token, long ttlSeconds) {
        redisTemplate.expire(token, Duration.ofSeconds(ttlSeconds));
    }

    public void revokeSession(String token) {
        redisTemplate.delete(token);
    }
}

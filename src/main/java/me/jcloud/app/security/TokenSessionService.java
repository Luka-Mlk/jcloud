package me.jcloud.app.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class TokenSessionService {
    private final StringRedisTemplate redisTemplate;

    public TokenSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void activateSession(String token, UUID userId, long ttlSeconds) {
        redisTemplate.opsForValue().set(token, userId.toString(), Duration.ofSeconds(ttlSeconds));
    }

    public boolean isSessionActive(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(token));
    }

    public void revokeSession(String token) {
        redisTemplate.delete(token);
    }
}

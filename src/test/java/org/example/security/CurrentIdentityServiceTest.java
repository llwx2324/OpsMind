package org.example.security;

import org.example.config.SecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 JWT 用户、租户声明解析及冲突声明拒绝行为。 */
class CurrentIdentityServiceTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void readsSubjectAndTenantFromValidatedJwt() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        Jwt jwt = jwt(Map.of("tenant_id", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(jwt, "n/a"));
        CallerIdentity identity = new CurrentIdentityService(properties).requireIdentity();
        assertEquals("user-a", identity.userId());
        assertEquals("tenant-a", identity.tenantId());
    }

    @Test
    void rejectsConflictingTenantClaims() {
        Jwt jwt = jwt(Map.of("tenant_id", "tenant-a", "tid", "tenant-b"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(jwt, "n/a"));
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        assertThrows(AccessDeniedException.class, () -> new CurrentIdentityService(properties).requireIdentity());
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), new java.util.HashMap<>() {{ put("sub", "user-a"); putAll(claims); }});
    }
}

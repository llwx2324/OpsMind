package org.example.security;

import org.example.config.SecurityProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
/** 从 Spring Security 上下文中解析并校验当前调用者的租户与用户身份。 */
public class CurrentIdentityService {
    private final SecurityProperties properties;
    public CurrentIdentityService(SecurityProperties properties) { this.properties = properties; }

    /**
     * 获取当前调用者身份；认证关闭时仅返回显式配置的本地开发身份。
     *
     * @return 可信的租户与用户标识
     * @throws AccessDeniedException JWT 缺失、身份声明缺失或租户声明冲突时抛出
     */
    public CallerIdentity requireIdentity() {
        if (!properties.isEnabled()) {
            return new CallerIdentity(properties.getDevTenantId(), properties.getDevUserId());
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("JWT authentication is required");
        }
        String userId = jwt.getSubject();
        String tenantId = jwt.getClaimAsString(properties.getTenantClaim());
        String fallbackTenantId = jwt.getClaimAsString("tid");
        // 同时存在两个租户声明时必须完全一致，防止调用者利用声明歧义绕过租户隔离。
        if (tenantId != null && fallbackTenantId != null && !tenantId.equals(fallbackTenantId)) {
            throw new AccessDeniedException("Conflicting tenant claims in JWT");
        }
        if (tenantId == null || tenantId.isBlank()) tenantId = fallbackTenantId;
        if (userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()) {
            throw new AccessDeniedException("JWT must contain sub and tenant claim");
        }
        return new CallerIdentity(tenantId, userId);
    }
}

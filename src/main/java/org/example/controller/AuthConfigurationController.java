package org.example.controller;

import org.example.config.SecurityProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** 向前端公开 OIDC 登录所需的非敏感元数据，绝不返回客户端密钥。 */
@RestController
public class AuthConfigurationController {
    private final SecurityProperties properties;
    public AuthConfigurationController(SecurityProperties properties) { this.properties = properties; }
    @GetMapping("/api/public/auth-config")
    /** 获取 Authorization Code + PKCE 登录所需的公开配置。 */
    public Map<String, Object> config() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "issuer", properties.getIssuerUri() == null ? "" : properties.getIssuerUri(),
                "audience", properties.getAudience() == null ? "" : properties.getAudience(),
                "clientId", properties.getClientId() == null ? "" : properties.getClientId(),
                "scopes", properties.getScopes());
    }
}

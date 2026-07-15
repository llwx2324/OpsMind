package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opsmind.security")
/** OIDC/JWT 身份校验及本地开发身份配置。 */
public class SecurityProperties {
    /** 是否启用真实 OIDC 认证。 */
    private boolean enabled;
    /** OIDC Provider 的 issuer URI。 */
    private String issuerUri;
    /** JWT 必须包含的目标 audience。 */
    private String audience;
    /** JWT 中租户标识的声明名。 */
    private String tenantClaim = "tenant_id";
    /** 静态前端执行 Authorization Code + PKCE 时使用的公开客户端 ID。 */
    private String clientId;
    /** 前端登录时请求的 OIDC scope。 */
    private String scopes = "openid profile";
    /** 关闭认证时使用的本地开发租户，不能用于生产环境。 */
    private String devTenantId = "dev-tenant";
    /** 关闭认证时使用的本地开发用户，不能用于生产环境。 */
    private String devUserId = "dev-user";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getTenantClaim() { return tenantClaim; }
    public void setTenantClaim(String tenantClaim) { this.tenantClaim = tenantClaim; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public String getDevTenantId() { return devTenantId; }
    public void setDevTenantId(String devTenantId) { this.devTenantId = devTenantId; }
    public String getDevUserId() { return devUserId; }
    public void setDevUserId(String devUserId) { this.devUserId = devUserId; }
}

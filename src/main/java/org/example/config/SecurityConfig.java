package org.example.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties({ChatProperties.class, SecurityProperties.class})
/** 配置 OIDC Resource Server、JWT issuer/audience 校验及开发模式访问策略。 */
public class SecurityConfig {
    @Bean
    @ConditionalOnProperty(prefix = "opsmind.security", name = "enabled", havingValue = "true")
    /** 创建同时校验 issuer 与 audience 的 JWT 解码器。 */
    org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder(SecurityProperties properties) {
        if (properties.getIssuerUri() == null || properties.getIssuerUri().isBlank() || properties.getAudience() == null || properties.getAudience().isBlank()) {
            throw new IllegalStateException("OIDC issuer-uri and audience must be configured when security is enabled");
        }
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(properties.getIssuerUri());
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(properties.getAudience())
                ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.getIssuerUri()), audience));
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(prefix = "opsmind.security", name = "enabled", havingValue = "true")
    /** 认证启用时保护全部聊天接口和非健康检查 Actuator 接口。 */
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/legacy-disabled/**").denyAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/api/chat/**", "/api/v2/chat/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "opsmind.security", name = "enabled", havingValue = "false", matchIfMissing = true)
    /** 本地开发模式访问链；身份由显式配置的开发租户和用户提供。 */
    SecurityFilterChain developmentSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/legacy-disabled/**").denyAll()
                        .anyRequest().permitAll())
                .build();
    }
}

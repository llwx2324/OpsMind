package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 连接配置。
 *
 * <p>绑定 application.yml 中的 milvus 前缀配置，用于控制连接地址、鉴权信息、数据库与超时时间。</p>
 */
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    /**
     * Milvus host，默认 localhost
     */
    private String host = "localhost";

    /**
     * Milvus port，默认 19530
     */
    private Integer port = 19530;

    /**
     * 可选的连接用户名（若 Milvus 开启鉴权，则需要配置）
     */
    private String username = "";

    /**
     * 可选的连接密码（若 Milvus 开启鉴权，则需要配置）
     */
    private String password = "";

    /**
     * 目标数据库名，默认 default
     */
    private String database = "default";

    /**
     * 连接超时时间（毫秒），默认 10000
     */
    private Long timeout = 10000L;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getAddress() {
        return host + ":" + port;
    }
}

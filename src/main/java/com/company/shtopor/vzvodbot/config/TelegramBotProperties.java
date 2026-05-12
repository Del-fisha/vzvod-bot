package com.company.shtopor.vzvodbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    private String token = "";
    private String username = "vzvod_bot";

    /** Если до api.telegram.org только через прокси (корпоративная сеть). */
    private String proxyHost = "";
    private int proxyPort = 0;
    /** HTTP (по умолчанию при указанном хосте), SOCKS4, SOCKS5 */
    private String proxyType = "HTTP";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost == null ? "" : proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyType() {
        return proxyType;
    }

    public void setProxyType(String proxyType) {
        this.proxyType = proxyType == null ? "HTTP" : proxyType;
    }
}

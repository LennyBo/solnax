package com.rose.solnax.process.adapters.meters.shelly;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level configuration for all Shelly EM3 devices.
 *
 * Example application.yml:
 *
 * shelly:
 *   em3:
 *     connect-timeout: 3s
 *     read-timeout: 5s
 *     devices:
 *       garage:
 *         host: 192.168.1.100
 *       solar:
 *         host: 192.168.1.101
 *         username: admin
 *         password: secret
 *       main-panel:
 *         host: 192.168.1.102
 *         port: 8080
 */
@Component
@ConfigurationProperties(prefix = "shelly.em3")
public class ShellyEm3Properties {

    /** Shared connection timeout applied to all devices (can be overridden per device). */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** Shared read timeout applied to all devices (can be overridden per device). */
    private Duration readTimeout = Duration.ofSeconds(5);

    /** Named device map. Key = logical name used throughout the application. */
    private Map<String, Device> devices = new LinkedHashMap<>();

    // -------------------------------------------------------------------------

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

    public Map<String, Device> getDevices() { return devices; }
    public void setDevices(Map<String, Device> devices) { this.devices = devices; }

    // -------------------------------------------------------------------------

    public static class Device {

        private String host;
        private int port = 80;
        private String username = "";
        private String password = "";

        /** Per-device override; falls back to the shared value when null. */
        private Duration connectTimeout;
        private Duration readTimeout;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

        public String baseUrl() {
            return "http://" + host + (port != 80 ? ":" + port : "");
        }
    }
}
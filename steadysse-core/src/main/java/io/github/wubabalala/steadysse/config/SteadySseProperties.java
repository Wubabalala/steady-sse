package io.github.wubabalala.steadysse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for SteadySSE.
 * <p>
 * Prefix: {@code steady-sse}
 * <p>
 * Example:
 * <pre>
 * steady-sse:
 *   max-concurrent: 100
 *   heartbeat-interval: 10s
 *   cleanup-interval: 30s
 *   timeout:
 *     first-chunk: 30s
 *     idle: 60s
 *     hard: 300s
 * </pre>
 */
@ConfigurationProperties(prefix = "steady-sse")
public class SteadySseProperties {

    private int maxConcurrent = 100;
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private Duration cleanupInterval = Duration.ofSeconds(30);
    private TimeoutConfig timeout = new TimeoutConfig();

    public static class TimeoutConfig {
        private Duration firstChunk = Duration.ofSeconds(30);
        private Duration idle = Duration.ofSeconds(60);
        private Duration hard = Duration.ofSeconds(300);

        public Duration getFirstChunk() { return firstChunk; }
        public void setFirstChunk(Duration firstChunk) { this.firstChunk = firstChunk; }
        public Duration getIdle() { return idle; }
        public void setIdle(Duration idle) { this.idle = idle; }
        public Duration getHard() { return hard; }
        public void setHard(Duration hard) { this.hard = hard; }
    }

    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    public TimeoutConfig getTimeout() { return timeout; }
    public void setTimeout(TimeoutConfig timeout) { this.timeout = timeout; }
}

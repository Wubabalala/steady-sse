package io.github.wubabalala.steadysse.config;

import io.github.wubabalala.steadysse.cancel.HttpCallCancellationManager;
import io.github.wubabalala.steadysse.manager.SseConnectionManager;
import io.github.wubabalala.steadysse.manager.SseHeartbeatDetector;
import io.github.wubabalala.steadysse.manager.SseTimeoutDetector;
import io.github.wubabalala.steadysse.metrics.SseConnectionEndpoint;
import io.github.wubabalala.steadysse.metrics.SseConnectionMetrics;
import io.github.wubabalala.steadysse.spi.TimeoutProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring Boot auto-configuration for SteadySSE.
 * <p>
 * Wires all core components with sensible defaults. Scheduling is handled
 * by {@link SteadySseSchedulingConfiguration} to avoid circular dependencies.
 * <p>
 * All beans are {@code @ConditionalOnMissingBean} — users can override any bean.
 */
@AutoConfiguration
@EnableConfigurationProperties(SteadySseProperties.class)
public class SteadySseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SseConnectionMetrics sseConnectionMetrics() {
        return new SseConnectionMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public SseConnectionManager sseConnectionManager(SteadySseProperties properties,
                                                      SseConnectionMetrics metrics) {
        return new SseConnectionManager(properties, metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpCallCancellationManager httpCallCancellationManager() {
        return new HttpCallCancellationManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public SseHeartbeatDetector sseHeartbeatDetector(SseConnectionManager connectionManager) {
        return new SseHeartbeatDetector(connectionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SseTimeoutDetector sseTimeoutDetector(SseConnectionManager connectionManager,
                                                  SteadySseProperties properties,
                                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                                  TimeoutProvider timeoutProvider) {
        return new SseTimeoutDetector(connectionManager, properties, timeoutProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public SseConnectionEndpoint sseConnectionEndpoint(SseConnectionManager connectionManager) {
        return new SseConnectionEndpoint(connectionManager);
    }

    /**
     * Default TaskScheduler for heartbeat and timeout tasks.
     * <p>
     * Uses 2 threads (one for heartbeat, one for timeout) to prevent them from
     * blocking each other. Users can override by defining their own {@code TaskScheduler} bean.
     * <p>
     * For high-throughput scenarios on Java 21+, consider providing a virtual-thread-based
     * scheduler or increasing the pool size.
     */
    @Bean(name = "steadySseTaskScheduler")
    @ConditionalOnMissingBean(name = "steadySseTaskScheduler")
    public TaskScheduler steadySseTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("steadysse-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}

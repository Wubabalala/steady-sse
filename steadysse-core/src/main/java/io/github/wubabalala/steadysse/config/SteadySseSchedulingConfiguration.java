package io.github.wubabalala.steadysse.config;

import io.github.wubabalala.steadysse.manager.SseHeartbeatDetector;
import io.github.wubabalala.steadysse.manager.SseTimeoutDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Auto-registers scheduled tasks for heartbeat and timeout detection.
 * <p>
 * Separated from {@link SteadySseAutoConfiguration} to avoid circular dependency
 * (this config injects beans defined in the other config).
 * <p>
 * Tasks run at intervals configured via {@code steady-sse.heartbeat-interval}
 * and {@code steady-sse.cleanup-interval}.
 */
@AutoConfiguration(after = SteadySseAutoConfiguration.class)
@ConditionalOnBean(SteadySseAutoConfiguration.class)
@EnableScheduling
public class SteadySseSchedulingConfiguration implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SteadySseSchedulingConfiguration.class);

    private final SteadySseProperties properties;
    private final SseHeartbeatDetector heartbeatDetector;
    private final SseTimeoutDetector timeoutDetector;

    public SteadySseSchedulingConfiguration(SteadySseProperties properties,
                                             SseHeartbeatDetector heartbeatDetector,
                                             SseTimeoutDetector timeoutDetector) {
        this.properties = properties;
        this.heartbeatDetector = heartbeatDetector;
        this.timeoutDetector = timeoutDetector;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        log.info("[SteadySSE] Scheduling heartbeat every {} and timeout check every {}",
                properties.getHeartbeatInterval(), properties.getCleanupInterval());

        taskRegistrar.addFixedRateTask(
                heartbeatDetector::sendHeartbeats,
                properties.getHeartbeatInterval()
        );

        taskRegistrar.addFixedRateTask(
                timeoutDetector::checkTimeouts,
                properties.getCleanupInterval()
        );
    }
}

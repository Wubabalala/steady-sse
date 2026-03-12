package io.github.wubabalala.steadysse.config;

import io.github.wubabalala.steadysse.manager.SseHeartbeatDetector;
import io.github.wubabalala.steadysse.manager.SseTimeoutDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Auto-registers scheduled tasks for heartbeat and timeout detection.
 * <p>
 * Uses the {@code steadySseTaskScheduler} bean (2-thread pool by default)
 * to ensure heartbeat and timeout checks run independently and don't
 * block each other or the application's other scheduled tasks.
 * <p>
 * Users can override the scheduler by defining their own {@code steadySseTaskScheduler}
 * bean — for example, using Java 21 virtual threads for high-throughput scenarios.
 */
@AutoConfiguration(after = SteadySseAutoConfiguration.class)
@ConditionalOnBean(SteadySseAutoConfiguration.class)
@EnableScheduling
public class SteadySseSchedulingConfiguration implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SteadySseSchedulingConfiguration.class);

    private final SteadySseProperties properties;
    private final SseHeartbeatDetector heartbeatDetector;
    private final SseTimeoutDetector timeoutDetector;
    private final TaskScheduler taskScheduler;

    public SteadySseSchedulingConfiguration(SteadySseProperties properties,
                                             SseHeartbeatDetector heartbeatDetector,
                                             SseTimeoutDetector timeoutDetector,
                                             @Qualifier("steadySseTaskScheduler") TaskScheduler taskScheduler) {
        this.properties = properties;
        this.heartbeatDetector = heartbeatDetector;
        this.timeoutDetector = timeoutDetector;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler);

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

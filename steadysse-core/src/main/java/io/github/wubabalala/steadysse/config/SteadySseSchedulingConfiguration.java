package io.github.wubabalala.steadysse.config;

import io.github.wubabalala.steadysse.manager.SseHeartbeatDetector;
import io.github.wubabalala.steadysse.manager.SseTimeoutDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.ScheduledFuture;

/**
 * Auto-registers internal scheduled tasks for heartbeat and timeout detection.
 * <p>
 * Uses the {@code steadySseTaskScheduler} bean (2-thread pool by default)
 * to ensure heartbeat and timeout checks run independently and don't
 * block each other or the application's other scheduled tasks.
 * <p>
 * Important: this configuration schedules SteadySSE's own background tasks directly
 * and does NOT enable Spring's global {@code @Scheduled} infrastructure for the host app.
 */
@AutoConfiguration(after = SteadySseAutoConfiguration.class)
public class SteadySseSchedulingConfiguration implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SteadySseSchedulingConfiguration.class);

    private final SteadySseProperties properties;
    private final SseHeartbeatDetector heartbeatDetector;
    private final SseTimeoutDetector timeoutDetector;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> timeoutTask;

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
    public void afterPropertiesSet() {
        log.info("[SteadySSE] Scheduling heartbeat every {} and timeout check every {}",
                properties.getHeartbeatInterval(), properties.getCleanupInterval());

        heartbeatTask = taskScheduler.scheduleAtFixedRate(
                heartbeatDetector::sendHeartbeats,
                properties.getHeartbeatInterval());
        timeoutTask = taskScheduler.scheduleAtFixedRate(
                timeoutDetector::checkTimeouts,
                properties.getCleanupInterval());
    }

    @Override
    public void destroy() {
        cancelTask(heartbeatTask, "heartbeat");
        cancelTask(timeoutTask, "timeout");
    }

    private void cancelTask(ScheduledFuture<?> task, String taskName) {
        if (task != null) {
            task.cancel(true);
            log.debug("[SteadySSE] Cancelled {} task", taskName);
        }
    }
}

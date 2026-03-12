package io.github.wubabalala.steadysse.config;

import io.github.wubabalala.steadysse.cancel.HttpCallCancellationManager;
import io.github.wubabalala.steadysse.manager.SseConnectionManager;
import io.github.wubabalala.steadysse.manager.SseHeartbeatDetector;
import io.github.wubabalala.steadysse.manager.SseTimeoutDetector;
import io.github.wubabalala.steadysse.metrics.SseConnectionEndpoint;
import io.github.wubabalala.steadysse.metrics.SseConnectionMetrics;
import io.github.wubabalala.steadysse.spi.TimeoutProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SteadySseAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SteadySseAutoConfiguration.class,
                    SteadySseSchedulingConfiguration.class));

    @Test
    void allCoreBeansAutoConfigured() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SteadySseProperties.class);
            assertThat(context).hasSingleBean(SseConnectionManager.class);
            assertThat(context).hasSingleBean(HttpCallCancellationManager.class);
            assertThat(context).hasSingleBean(SseHeartbeatDetector.class);
            assertThat(context).hasSingleBean(SseTimeoutDetector.class);
            assertThat(context).hasSingleBean(SseConnectionMetrics.class);
        });
    }

    @Test
    void defaultMaxConcurrentIs100() {
        runner.run(context -> {
            var manager = context.getBean(SseConnectionManager.class);
            assertThat(manager.getMaxConcurrent()).isEqualTo(100);
        });
    }

    @Test
    void customPropertiesApplied() {
        runner.withPropertyValues("steady-sse.max-concurrent=50")
                .run(context -> {
                    var manager = context.getBean(SseConnectionManager.class);
                    assertThat(manager.getMaxConcurrent()).isEqualTo(50);
                });
    }

    @Test
    void customTimeoutProviderUsed() {
        runner.withUserConfiguration(CustomTimeoutProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TimeoutProvider.class);
                    assertThat(context).hasSingleBean(SseTimeoutDetector.class);
                });
    }

    @Test
    void actuatorEndpointConditionalOnActuator() {
        // Default runner has actuator on classpath (test dependency)
        runner.run(context -> {
            assertThat(context).hasSingleBean(SseConnectionEndpoint.class);
        });
    }

    @Test
    void customConnectionManagerBeanIsRespected() {
        runner.withUserConfiguration(CustomConnectionManagerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SseConnectionManager.class);
                    var manager = context.getBean(SseConnectionManager.class);
                    // Custom one has maxConcurrent=5
                    assertThat(manager.getMaxConcurrent()).isEqualTo(5);
                });
    }

    @Test
    void schedulingConfigurationIsRegistered() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SteadySseSchedulingConfiguration.class);
        });
    }

    @Test
    void defaultTaskSchedulerHasTwoThreads() {
        runner.run(context -> {
            var scheduler = context.getBean("steadySseTaskScheduler", TaskScheduler.class);
            assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
            assertThat(((ThreadPoolTaskScheduler) scheduler).getPoolSize()).isEqualTo(2);
        });
    }

    @Test
    void customTaskSchedulerIsRespected() {
        runner.withUserConfiguration(CustomSchedulerConfig.class)
                .run(context -> {
                    var scheduler = context.getBean("steadySseTaskScheduler", TaskScheduler.class);
                    assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
                    // Custom one has pool size 4
                    assertThat(((ThreadPoolTaskScheduler) scheduler).getPoolSize()).isEqualTo(4);
                });
    }

    // === Test configurations ===

    static class CustomTimeoutProviderConfig {
        @org.springframework.context.annotation.Bean
        TimeoutProvider timeoutProvider() {
            return new TimeoutProvider() {
                @Override public Duration getFirstChunkTimeout(String key) { return Duration.ofSeconds(5); }
                @Override public Duration getIdleTimeout(String key) { return Duration.ofSeconds(10); }
                @Override public Duration getHardTimeout(String key) { return Duration.ofSeconds(60); }
            };
        }
    }

    static class CustomConnectionManagerConfig {
        @org.springframework.context.annotation.Bean
        SseConnectionManager sseConnectionManager() {
            var props = new SteadySseProperties();
            props.setMaxConcurrent(5);
            return new SseConnectionManager(props);
        }
    }

    static class CustomSchedulerConfig {
        @org.springframework.context.annotation.Bean(name = "steadySseTaskScheduler")
        TaskScheduler steadySseTaskScheduler() {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(4);
            scheduler.setThreadNamePrefix("custom-sse-");
            return scheduler;
        }
    }
}

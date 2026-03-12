package io.github.wubabalala.steadysse.spi;

import java.time.Duration;

/**
 * SPI for providing per-connection timeout overrides.
 * <p>
 * If a bean implementing this interface is present in the application context,
 * it takes precedence over the static values in {@code SteadySseProperties.TimeoutConfig}.
 * If not present, defaults from properties are used.
 * <p>
 * The key parameter is the connection key used in {@code SseConnectionManager.register()}.
 */
public interface TimeoutProvider {

    Duration getFirstChunkTimeout(String key);

    Duration getIdleTimeout(String key);

    Duration getHardTimeout(String key);
}

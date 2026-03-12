package io.github.wubabalala.steadysse.emitter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FlushingSseEmitterUnitTest {

    @Test
    void constructorSetsResponseHeaders() {
        var response = new MockHttpServletResponse();
        new FlushingSseEmitter(30000L, response);

        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeader("Content-Encoding")).isEqualTo("identity");
        assertThat(response.getHeader("Cache-Control")).contains("no-cache");
    }

    @Test
    void contentTypeSetToEventStream() {
        var response = new MockHttpServletResponse();
        new FlushingSseEmitter(30000L, response);

        assertThat(response.getContentType()).isEqualTo("text/event-stream");
    }

    @Test
    void nullResponseDoesNotThrow() {
        assertDoesNotThrow(() -> new FlushingSseEmitter(30000L, null));
    }
}

package io.github.wubabalala.steadysse.protocol;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SseEventsTest {

    @Test
    void doneEventBuildsWithoutException() {
        SseEmitter.SseEventBuilder builder = SseEvents.done();
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotEmpty();
    }

    @Test
    void errorEventBuildsWithoutException() {
        SseEmitter.SseEventBuilder builder = SseEvents.error("fail", "ERR_01");
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotEmpty();
    }

    @Test
    void errorEventWithSpecialCharsDoesNotThrow() {
        assertDoesNotThrow(() -> SseEvents.error("line1\nline2\"quoted\"\\backslash", null));
    }

    @Test
    void retryEventBuildsWithoutException() {
        SseEmitter.SseEventBuilder builder = SseEvents.retry();
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotEmpty();
    }

    @Test
    void chunkEventBuildsWithoutException() {
        SseEmitter.SseEventBuilder builder = SseEvents.chunk("hello world");
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotEmpty();
    }

    @Test
    void eventConstants() {
        assertThat(SseEvents.EVENT_DONE).isEqualTo("done");
        assertThat(SseEvents.EVENT_ERROR).isEqualTo("error");
        assertThat(SseEvents.EVENT_RETRY).isEqualTo("retry");
    }
}

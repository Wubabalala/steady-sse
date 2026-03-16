package io.github.wubabalala.steadysse.emitter;

import jakarta.servlet.ServletOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void bufferSizeFailureFallsBackToFlushLayers() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new IllegalArgumentException("buffer size 0 unsupported"))
                .when(response).setBufferSize(0);

        var emitter = new FlushingSseEmitter(30000L, response);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(emitter, "forceFlush"));
        verify(response).flushBuffer();
        verify(outputStream).flush();
    }

    @Test
    void bufferSizeFailureDisablesFutureBufferTuning() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new IllegalArgumentException("buffer size 0 unsupported"))
                .when(response).setBufferSize(0);

        var emitter = new FlushingSseEmitter(30000L, response);

        ReflectionTestUtils.invokeMethod(emitter, "forceFlush");
        ReflectionTestUtils.invokeMethod(emitter, "forceFlush");

        verify(response, times(1)).setBufferSize(0);
        verify(response, times(2)).flushBuffer();
        verify(outputStream, times(2)).flush();
    }
}

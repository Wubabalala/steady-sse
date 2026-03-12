package io.github.wubabalala.steadysse.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SseExceptionClassifierTest {

    @Test
    void brokenPipeIsClientDisconnect() {
        var ex = new IOException("Broken pipe");
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isTrue();
    }

    @Test
    void connectionResetIsClientDisconnect() {
        var ex = new IOException("Connection reset by peer");
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isTrue();
    }

    @Test
    void socketClosedIsClientDisconnect() {
        var ex = new IOException("Socket closed");
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isTrue();
    }

    @Test
    void forciblyClosedIsClientDisconnect() {
        var ex = new IOException("An existing connection was forcibly closed");
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isTrue();
    }

    @Test
    void clientAbortExceptionIsClientDisconnect() {
        var ex = new IOException("ClientAbortException");
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isTrue();
    }

    @Test
    void otherIOExceptionIsNotClientDisconnect() {
        var ex = new IOException("Read timed out");
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isFalse();
    }

    @Test
    void nullMessageIsNotClientDisconnect() {
        var ex = new IOException((String) null);
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isFalse();
    }

    @Test
    void nestedCauseIsDetected() {
        var root = new IOException("Broken pipe");
        var wrapper = new RuntimeException("wrapper", root);
        assertThat(SseExceptionClassifier.isClientDisconnect(wrapper)).isTrue();
    }

    @Test
    void deeplyNestedCauseIsDetected() {
        var root = new IOException("Connection reset by peer");
        var mid = new RuntimeException("mid", root);
        var outer = new IllegalStateException("outer", mid);
        assertThat(SseExceptionClassifier.isClientDisconnect(outer)).isTrue();
    }

    @Test
    void nonIOExceptionReturnsFalse() {
        var ex = new RuntimeException("Broken pipe");
        assertThat(SseExceptionClassifier.isClientDisconnect(ex)).isFalse();
    }
}

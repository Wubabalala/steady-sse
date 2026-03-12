package io.github.wubabalala.steadysse.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class SseEvents {

    public static final String EVENT_DONE = "done";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_RETRY = "retry";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseEvents() {}

    public static SseEmitter.SseEventBuilder done() {
        return SseEmitter.event().name(EVENT_DONE).data("");
    }

    public static SseEmitter.SseEventBuilder error(String message, String code) {
        String json = toJson(SseEventPayload.error(message, code));
        return SseEmitter.event().name(EVENT_ERROR).data(json, MediaType.APPLICATION_JSON);
    }

    public static SseEmitter.SseEventBuilder retry() {
        String json = toJson(SseEventPayload.retry());
        return SseEmitter.event().name(EVENT_RETRY).data(json, MediaType.APPLICATION_JSON);
    }

    public static SseEmitter.SseEventBuilder chunk(String content) {
        return SseEmitter.event().data(content);
    }

    public static void sendDone(SseEmitter emitter) throws IOException {
        emitter.send(done());
    }

    public static void sendError(SseEmitter emitter, String message, String code) throws IOException {
        emitter.send(error(message, code));
    }

    public static void sendRetry(SseEmitter emitter) throws IOException {
        emitter.send(retry());
    }

    public static void sendChunk(SseEmitter emitter, String content) throws IOException {
        emitter.send(chunk(content));
    }

    private static String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize SSE event payload", e);
        }
    }
}

package io.github.wubabalala.steadysse.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseEventPayload {

    private String message;
    private String code;
    private Boolean clear;

    private SseEventPayload() {}

    public static SseEventPayload error(String message, String code) {
        var payload = new SseEventPayload();
        payload.message = message;
        payload.code = code;
        return payload;
    }

    public static SseEventPayload retry() {
        var payload = new SseEventPayload();
        payload.clear = true;
        return payload;
    }

    public String getMessage() { return message; }
    public String getCode() { return code; }
    public Boolean getClear() { return clear; }
}

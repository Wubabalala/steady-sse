package io.github.wubabalala.steadysse.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void errorPayloadSerializesToJson() throws Exception {
        var payload = SseEventPayload.error("something failed", "TIMEOUT");
        String json = MAPPER.writeValueAsString(payload);

        var node = MAPPER.readTree(json);
        assertThat(node.get("message").asText()).isEqualTo("something failed");
        assertThat(node.get("code").asText()).isEqualTo("TIMEOUT");
    }

    @Test
    void errorPayloadWithNullCode() throws Exception {
        var payload = SseEventPayload.error("msg", null);
        String json = MAPPER.writeValueAsString(payload);

        var node = MAPPER.readTree(json);
        assertThat(node.get("message").asText()).isEqualTo("msg");
        assertThat(node.has("code")).isFalse();
    }

    @Test
    void errorPayloadWithSpecialCharacters() throws Exception {
        var payload = SseEventPayload.error("line1\nline2\"quoted\"", null);
        String json = MAPPER.writeValueAsString(payload);

        var deserialized = MAPPER.readValue(json, SseEventPayload.class);
        assertThat(deserialized.getMessage()).isEqualTo("line1\nline2\"quoted\"");
    }

    @Test
    void retryPayloadHasClearTrue() throws Exception {
        var payload = SseEventPayload.retry();
        String json = MAPPER.writeValueAsString(payload);

        var node = MAPPER.readTree(json);
        assertThat(node.get("clear").asBoolean()).isTrue();
    }

    @Test
    void retryPayloadHasNoMessageField() throws Exception {
        var payload = SseEventPayload.retry();
        String json = MAPPER.writeValueAsString(payload);

        var node = MAPPER.readTree(json);
        assertThat(node.has("message")).isFalse();
        assertThat(node.has("code")).isFalse();
    }
}

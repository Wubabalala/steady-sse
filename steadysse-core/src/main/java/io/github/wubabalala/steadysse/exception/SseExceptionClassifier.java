package io.github.wubabalala.steadysse.exception;

import java.io.IOException;
import java.util.List;

public final class SseExceptionClassifier {

    private SseExceptionClassifier() {}

    private static final List<String> CLIENT_DISCONNECT_MARKERS = List.of(
        "Broken pipe",
        "ClientAbortException",
        "Connection reset by peer",
        "Socket closed",
        "An existing connection was forcibly closed"
    );

    public static boolean isClientDisconnect(IOException exception) {
        String message = exception.getMessage();
        if (message == null) return false;
        return CLIENT_DISCONNECT_MARKERS.stream().anyMatch(message::contains);
    }

    public static boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException io && isClientDisconnect(io)) return true;
            current = current.getCause();
        }
        return false;
    }
}

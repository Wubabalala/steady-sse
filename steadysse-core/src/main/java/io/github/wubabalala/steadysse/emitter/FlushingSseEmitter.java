package io.github.wubabalala.steadysse.emitter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * An SseEmitter that forces immediate flush after every send.
 * <p>
 * Solves the Spring SseEmitter buffering problem: standard {@code send()} writes data
 * to a buffer but does not immediately flush, causing multiple chunks to accumulate
 * before being sent to the client.
 * <p>
 * Uses a three-layer flush strategy to penetrate all buffering layers:
 * <ol>
 *   <li>{@code setBufferSize(0)} — disable servlet response buffering</li>
 *   <li>{@code flushBuffer()} — flush the servlet response buffer</li>
 *   <li>{@code outputStream.flush()} — flush the underlying output stream</li>
 * </ol>
 */
public class FlushingSseEmitter extends SseEmitter {

    private static final Logger log = LoggerFactory.getLogger(FlushingSseEmitter.class);

    private final HttpServletResponse response;

    public FlushingSseEmitter(Long timeout, HttpServletResponse response) {
        super(timeout);
        this.response = response;
        configureResponse();
    }

    /**
     * Configure response headers to disable all buffering.
     */
    private void configureResponse() {
        if (response == null) {
            return;
        }

        try {
            if (isResponseRecycled()) {
                log.trace("[SteadySSE] Response already recycled, skipping configuration");
                return;
            }

            response.setContentType("text/event-stream");
            // Disable reverse proxy buffering (Nginx/Apache)
            response.setHeader("X-Accel-Buffering", "no");
            // Prevent gzip compression (causes buffering)
            response.setHeader("Content-Encoding", "identity");
            // Instruct clients not to cache
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        } catch (Exception e) {
            log.warn("[SteadySSE] Failed to configure response headers: {}", e.getMessage());
        }
    }

    @Override
    public void send(Object object) throws IOException {
        super.send(object);
        forceFlush();
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        super.send(builder);
        forceFlush();
    }

    /**
     * Three-layer forced flush to ensure data is immediately sent to the client.
     */
    private void forceFlush() {
        if (response == null) {
            return;
        }

        if (isResponseRecycled()) {
            log.trace("[SteadySSE] Response recycled, skipping flush");
            return;
        }

        try {
            // Layer 0: Re-set Content-Type if overridden by Spring
            if (!response.isCommitted()) {
                String currentContentType = response.getContentType();
                if (!"text/event-stream".equals(currentContentType)) {
                    log.warn("[SteadySSE] Content-Type was overridden to: {}, forcing back to text/event-stream",
                            currentContentType);
                    response.setContentType("text/event-stream");
                }
                // Layer 1: Reset buffer size to 0 (disable buffering)
                response.setBufferSize(0);
            }

            // Layer 2: Flush servlet response buffer
            response.flushBuffer();

            // Layer 3: Flush underlying output stream
            ServletOutputStream outputStream = response.getOutputStream();
            if (outputStream != null) {
                outputStream.flush();
            }
        } catch (IOException e) {
            // Client disconnect — normal during SSE, no need to log as error
            log.trace("[SteadySSE] Flush failed (client likely disconnected): {}", e.getMessage());
        }
    }

    /**
     * Check if the response object has been recycled by the container.
     *
     * @return true if the response is no longer usable
     */
    private boolean isResponseRecycled() {
        if (response == null) {
            return true;
        }

        try {
            response.isCommitted();
            return false;
        } catch (IllegalStateException e) {
            return true;
        } catch (Exception e) {
            log.warn("[SteadySSE] Exception checking response state: {}", e.getMessage());
            return true;
        }
    }
}

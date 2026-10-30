package test.unit.execution;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Drains one stream of a child process on its own daemon thread, decoding UTF-8 (multibyte-safe) and keeping at
 * most {@code maxChars} characters. After the limit it keeps reading and discarding, so the child never blocks
 * on a full pipe, and flags the output as truncated.
 */
final class OutputCollector implements Runnable {
    private final InputStream stream;
    private final int maxChars;
    private final StringBuilder buffer = new StringBuilder();
    private boolean truncated;
    private final Thread thread;

    OutputCollector(InputStream stream, int maxChars, String name) {
        this.stream = stream;
        this.maxChars = maxChars;
        this.thread = new Thread(this, name);
        this.thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    @Override
    public void run() {
        char[] chunk = new char[8192];
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            int n;
            while ((n = reader.read(chunk)) != -1) {
                append(chunk, n);
            }
        } catch (IOException ignored) {
            // Stream closed because the process died or was cleaned up: whatever was read is kept.
        }
    }

    private synchronized void append(char[] chunk, int n) {
        int room = maxChars - buffer.length();
        if (room >= n) {
            buffer.append(chunk, 0, n);
        } else {
            if (room > 0) buffer.append(chunk, 0, room);
            truncated = true;
        }
    }

    /** Waits (bounded) for the reader to reach end of stream. Restores the interrupt flag if interrupted. */
    void awaitEnd(long millis) {
        try {
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    synchronized String text() {
        return buffer.toString();
    }

    synchronized boolean truncated() {
        return truncated;
    }
}

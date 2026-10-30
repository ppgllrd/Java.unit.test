package test.unit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wire format between the parent JVM and a child JVM (version {@value #VERSION}). Package-private on purpose.
 * <p>
 * Both messages are UTF-8 text, one {@code key:base64(utf8 value)} line per field. Native Java serialization
 * is never used.
 * <ul>
 *   <li><b>Request</b>: written by the parent to the child's <em>stdin</em> (so that non-ASCII names do not
 *       depend on the platform's command-line encoding, and the secret nonce is not visible in the process list).</li>
 *   <li><b>Result</b>: written by the child to a private temporary <em>file</em> (never to stdout, which the test
 *       controls), atomically renamed when complete, and closed by an {@code end} line echoing the nonce.</li>
 * </ul>
 * The parent only accepts a result with the right version, the per-run random nonce, the expected test id and
 * the {@code end} marker.
 */
final class IsolationProtocol {
    static final int VERSION = 1;
    static final long MAX_RESULT_BYTES = 4L * 1024 * 1024;
    static final int MAX_TEXT_CHARS = 100_000;

    private IsolationProtocol() {}

    /** Result status as written by a child worker. */
    enum Status {
        PASSED(0), FAILED(1), ERROR(2), TIMED_OUT(3), CONFIGURATION_ERROR(4), INTERNAL_ERROR(5);

        /** Exit code the worker uses for this status; the parent cross-checks it with the real exit code. */
        final int exitCode;

        Status(int exitCode) { this.exitCode = exitCode; }
    }

    /** Malformed, incomplete or incompatible protocol data. */
    static final class ProtocolException extends IOException {
        private static final long serialVersionUID = 1L;
        ProtocolException(String message) { super(message); }
        ProtocolException(String message, Throwable cause) { super(message, cause); }
    }

    record WorkerRequest(String nonce, TestLocator locator, Language language, boolean ansi, int childTimeoutSeconds) {
        String encode() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("version", Integer.toString(VERSION));
            m.put("nonce", nonce);
            m.put("provider", locator.providerClass());
            m.put("suite", locator.suiteName());
            m.put("index", Integer.toString(locator.itemIndex()));
            m.put("name", locator.testName());
            m.put("class", locator.testClass());
            m.put("language", language.name());
            m.put("ansi", Boolean.toString(ansi));
            m.put("timeout", Integer.toString(childTimeoutSeconds));
            m.put("end", nonce);
            return IsolationProtocol.encode(m);
        }

        static WorkerRequest decode(String text) throws ProtocolException {
            Map<String, String> m = IsolationProtocol.decode(text);
            checkVersionAndEnd(m);
            try {
                var locator = new TestLocator(req(m, "provider"), req(m, "suite"), Integer.parseInt(req(m, "index")),
                        req(m, "name"), req(m, "class"));
                return new WorkerRequest(req(m, "nonce"), locator, Language.valueOf(req(m, "language")),
                        Boolean.parseBoolean(req(m, "ansi")), Integer.parseInt(req(m, "timeout")));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ProtocolException("invalid request: " + e.getMessage(), e);
            }
        }
    }

    record ChildResult(Status status, String testId, long durationNanos, String exceptionType, String exceptionMessage,
                       String stackTrace, String message, String description, int timeoutSeconds, String detail) {
        ChildResult {
            Objects.requireNonNull(status);
            Objects.requireNonNull(testId);
            exceptionType = nz(exceptionType);
            exceptionMessage = nz(exceptionMessage);
            stackTrace = nz(stackTrace);
            message = nz(message);
            description = nz(description);
            detail = nz(detail);
        }

        static ChildResult failure(Status status, String testId, String detail) {
            return new ChildResult(status, testId, 0, "", "", "", "", "", 0, detail);
        }
    }

    // --- Result file ---

    /** Writes the result atomically: complete content in a sibling temp file, then rename. */
    static void writeResult(Path resultFile, String nonce, ChildResult r) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("version", Integer.toString(VERSION));
        m.put("nonce", nonce);
        m.put("testId", r.testId());
        m.put("status", r.status().name());
        m.put("durationNanos", Long.toString(r.durationNanos()));
        m.put("exceptionType", limit(r.exceptionType()));
        m.put("exceptionMessage", limit(r.exceptionMessage()));
        m.put("stackTrace", limit(r.stackTrace()));
        m.put("message", limit(r.message()));
        m.put("description", limit(r.description()));
        m.put("timeoutSeconds", Integer.toString(r.timeoutSeconds()));
        m.put("detail", limit(r.detail()));
        m.put("end", nonce);
        Path tmp = resultFile.resolveSibling(resultFile.getFileName() + ".tmp");
        Files.writeString(tmp, encode(m), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, resultFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, resultFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Reads and validates a result file. Throws {@link ProtocolException} if absent, incomplete or foreign. */
    static ChildResult readResult(Path resultFile, String expectedNonce, String expectedTestId) throws IOException {
        if (!Files.isRegularFile(resultFile)) {
            throw new ProtocolException("result file was not produced");
        }
        if (Files.size(resultFile) > MAX_RESULT_BYTES) {
            throw new ProtocolException("result file is unreasonably large");
        }
        Map<String, String> m = decode(Files.readString(resultFile, StandardCharsets.UTF_8));
        checkVersionAndEnd(m);
        if (!expectedNonce.equals(m.get("end")) || !expectedNonce.equals(m.get("nonce"))) {
            throw new ProtocolException("result does not carry this run's nonce");
        }
        if (!expectedTestId.equals(m.get("testId"))) {
            throw new ProtocolException("result belongs to another test: " + m.get("testId"));
        }
        try {
            return new ChildResult(Status.valueOf(req(m, "status")), expectedTestId,
                    Long.parseLong(req(m, "durationNanos")), req(m, "exceptionType"), req(m, "exceptionMessage"),
                    req(m, "stackTrace"), req(m, "message"), req(m, "description"),
                    Integer.parseInt(req(m, "timeoutSeconds")), req(m, "detail"));
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("invalid result field: " + e.getMessage(), e);
        }
    }

    // --- Codec ---

    static String encode(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        var encoder = Base64.getEncoder();
        fields.forEach((k, v) -> sb.append(k).append(':')
                .append(encoder.encodeToString(v.getBytes(StandardCharsets.UTF_8))).append('\n'));
        return sb.toString();
    }

    static Map<String, String> decode(String text) throws ProtocolException {
        Map<String, String> m = new LinkedHashMap<>();
        var decoder = Base64.getDecoder();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon <= 0 || !line.substring(0, colon).chars().allMatch(Character::isLetterOrDigit)) {
                throw new ProtocolException("malformed line");
            }
            String key = line.substring(0, colon);
            try {
                String value = new String(decoder.decode(line.substring(colon + 1)), StandardCharsets.UTF_8);
                if (m.put(key, value) != null) {
                    throw new ProtocolException("duplicate key: " + key);
                }
            } catch (IllegalArgumentException e) {
                throw new ProtocolException("invalid base64 for key: " + key, e);
            }
        }
        return m;
    }

    private static void checkVersionAndEnd(Map<String, String> m) throws ProtocolException {
        String version = m.get("version");
        if (!Integer.toString(VERSION).equals(version)) {
            throw new ProtocolException("incompatible protocol version " + version + " (expected " + VERSION + ")");
        }
        if (m.get("end") == null) {
            throw new ProtocolException("incomplete data: missing end marker");
        }
    }

    private static String req(Map<String, String> m, String key) throws ProtocolException {
        String v = m.get(key);
        if (v == null) throw new ProtocolException("missing field: " + key);
        return v;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String limit(String s) {
        return s.length() <= MAX_TEXT_CHARS ? s : s.substring(0, MAX_TEXT_CHARS);
    }
}

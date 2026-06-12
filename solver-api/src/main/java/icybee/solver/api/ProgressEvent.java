package icybee.solver.api;

import org.jspecify.annotations.Nullable;

/**
 * A single event in a solve job's life. {@code type} is one of {@code progress}, {@code
 * completed}, {@code failed}, {@code cancelled}; the last three are terminal.
 */
public record ProgressEvent(
        String type,
        int iteration,
        double exploitability,
        long elapsedMs,
        @Nullable String error) {

    public static ProgressEvent progress(int iteration, double exploitability, long elapsedMs) {
        return new ProgressEvent("progress", iteration, exploitability, elapsedMs, null);
    }

    public static ProgressEvent completed() {
        return new ProgressEvent("completed", -1, 0, 0, null);
    }

    public static ProgressEvent failed(String error) {
        return new ProgressEvent("failed", -1, 0, 0, error);
    }

    public static ProgressEvent cancelled() {
        return new ProgressEvent("cancelled", -1, 0, 0, null);
    }

    public boolean isTerminal() {
        return !"progress".equals(type);
    }
}

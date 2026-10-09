package pokersolver.solver;

/**
 * Receives training progress after the first completed iteration, every {@code printInterval}
 * iterations, and the final iteration. Iterations are one-based; elapsedMs is cumulative training
 * time. Implementations must be fast and thread-safe; the callback runs on the training thread.
 */
@FunctionalInterface
public interface TrainingProgressListener {

    TrainingProgressListener NONE = (iteration, exploitability, elapsedMs) -> {};

    void onProgress(int iteration, float exploitability, long elapsedMs);
}

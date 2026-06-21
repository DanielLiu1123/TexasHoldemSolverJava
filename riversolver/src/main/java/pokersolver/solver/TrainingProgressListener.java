package pokersolver.solver;

/**
 * Receives training progress at the same cadence as {@code printInterval} (the iterations where
 * exploitability is evaluated). Implementations must be fast and thread-safe; the callback runs on
 * the training thread.
 */
@FunctionalInterface
public interface TrainingProgressListener {

    TrainingProgressListener NONE = (iteration, exploitability, elapsedMs) -> {};

    void onProgress(int iteration, float exploitability, long elapsedMs);
}

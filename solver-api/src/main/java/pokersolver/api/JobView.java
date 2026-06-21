package pokersolver.api;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The JSON projection of a {@link SolveJob} returned by the status endpoints. */
public record JobView(String id, String state, @Nullable String error, List<ProgressEvent> events) {

    public static JobView of(SolveJob job) {
        return new JobView(job.id(), job.state().name(), job.error(), job.events());
    }
}

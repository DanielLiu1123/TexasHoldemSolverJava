package icybee.solver.api;

import icybee.solver.solver.Solver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** One solve run: state machine, recorded events, and live subscribers (SSE clients). */
public final class SolveJob {

    public enum State {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String id;
    private final Instant createdAt = Instant.now();
    private final List<ProgressEvent> events = new ArrayList<>();
    private final List<Consumer<ProgressEvent>> subscribers = new CopyOnWriteArrayList<>();

    private volatile State state = State.RUNNING;
    private volatile @Nullable String error;
    private volatile @Nullable Solver solver;
    private volatile boolean cancelRequested;

    SolveJob(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public State state() {
        return state;
    }

    public @Nullable String error() {
        return error;
    }

    /** The trained solver (and its game tree), available once the run has produced a result. */
    public @Nullable Solver solver() {
        return solver;
    }

    public boolean cancelRequested() {
        return cancelRequested;
    }

    /** Publishes an event to the recorded history and all live subscribers. */
    synchronized void publish(ProgressEvent event) {
        events.add(event);
        for (Consumer<ProgressEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    /** Replays recorded events to the subscriber, then registers it for live events. */
    public synchronized void subscribe(Consumer<ProgressEvent> subscriber) {
        for (ProgressEvent event : events) {
            subscriber.accept(event);
        }
        subscribers.add(subscriber);
    }

    public void unsubscribe(Consumer<ProgressEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    public synchronized List<ProgressEvent> events() {
        return List.copyOf(events);
    }

    void attachSolver(Solver solver) {
        this.solver = solver;
        if (cancelRequested) solver.requestStop();
    }

    /** Requests cancellation; takes effect at the solver's next iteration boundary. */
    public void requestCancel() {
        cancelRequested = true;
        Solver attached = solver;
        if (attached != null) attached.requestStop();
    }

    /**
     * Marks the run finished. The strategy itself is not materialized here — it is served lazily,
     * one node at a time, by walking {@link #solver()}'s game tree (see {@code StrategyNodeView}),
     * because dumping the whole post-flop tree as one JSON blob can reach ~1 GB.
     */
    void complete() {
        if (cancelRequested) {
            this.state = State.CANCELLED;
            publish(ProgressEvent.cancelled());
        } else {
            this.state = State.COMPLETED;
            publish(ProgressEvent.completed());
        }
    }

    void fail(String error) {
        this.error = error;
        this.state = State.FAILED;
        publish(ProgressEvent.failed(error));
    }
}

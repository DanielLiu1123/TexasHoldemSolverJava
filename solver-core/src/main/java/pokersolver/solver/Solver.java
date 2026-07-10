package pokersolver.solver;

import pokersolver.GameTree;

/** Trains a strategy on a game tree until it converges or runs out of iterations. */
public abstract class Solver {

    private final GameTree tree;

    /** Set from any thread; observed by {@code train()} at each iteration boundary. */
    protected volatile boolean stopRequested;

    protected Solver(GameTree tree) {
        this.tree = tree;
    }

    public GameTree getTree() {
        return tree;
    }

    /**
     * Requests training to stop at the next iteration boundary. Safe to call from any thread;
     * {@link #train()} returns normally once the current iteration completes.
     */
    public void requestStop() {
        this.stopRequested = true;
    }

    public abstract void train() throws Exception;
}

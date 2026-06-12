package icybee.solver.solver;

import icybee.solver.GameTree;

/**
 * Created by huangxuefeng on 2019/10/9.
 * contains an abstract class Solver for cfr or other things.
 */
public abstract class Solver {

    GameTree tree;

    protected volatile boolean stopRequested;

    public Solver(GameTree tree) {
        this.tree = tree;
    }

    public GameTree getTree() {
        return tree;
    }

    /**
     * Requests training to stop at the next iteration boundary. Safe to call from any thread;
     * {@code train()} returns normally after the current iteration completes.
     */
    public void requestStop() {
        this.stopRequested = true;
    }

    public abstract void train() throws Exception;
}

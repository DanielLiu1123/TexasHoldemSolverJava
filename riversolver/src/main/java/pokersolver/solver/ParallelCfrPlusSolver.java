package pokersolver.solver;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.GameTreeNode;

/**
 * Parallel CFR solver: children are evaluated as forked ForkJoin tasks rather than in-line
 * recursion. Everything else — the traversal, the training loop — is shared with the single-threaded
 * solver via {@link AbstractCfrSolver}.
 *
 * <p>Forking at every action node measures fastest: each task does {@code O(actions × hands)} of
 * float work, well above ForkJoin's per-task overhead, and full forking gives the best load balance.
 * A subtree-size cutoff of 64 measured ~6x slower on river trees.
 */
public final class ParallelCfrPlusSolver extends AbstractCfrSolver {

    private static final Logger log = LoggerFactory.getLogger(ParallelCfrPlusSolver.class);

    private final ForkJoinPool forkJoinPool;
    private final double forkProbAction;
    private final double forkProbChance;
    private final int forkEveryNDepth;
    private final int noForkSubtreeSize;

    public ParallelCfrPlusSolver(
            SolverConfig config,
            int threads,
            double forkProbAction,
            double forkProbChance,
            int forkEveryNDepth,
            int noForkSubtreeSize) {
        super(config);
        int parallelism =
                switch (Integer.signum(threads)) {
                    case 1 -> threads;
                    case -1 -> {
                        if (threads != -1) throw new IllegalArgumentException("thread count not valid: " + threads);
                        yield Runtime.getRuntime().availableProcessors();
                    }
                    default -> throw new IllegalArgumentException("thread count not valid: " + threads);
                };
        requireProbability(forkProbAction, "fork-at-action");
        requireProbability(forkProbChance, "fork-at-chance");

        this.forkJoinPool = new ForkJoinPool(parallelism);
        this.forkProbAction = forkProbAction;
        this.forkProbChance = forkProbChance;
        this.forkEveryNDepth = forkEveryNDepth;
        this.noForkSubtreeSize = noForkSubtreeSize;
        log.info("solving with {} threads", parallelism);
    }

    private static void requireProbability(double value, String name) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("%s must be in [0, 1], got %s".formatted(name, value));
        }
    }

    @Override
    protected float[] traverse(
            int player, GameTreeNode root, float[][] reachProbs, int iteration, long board, double[] roundDeals) {
        return forkJoinPool.invoke(new CfrTask(player, root, reachProbs, iteration, board, roundDeals));
    }

    @Override
    protected void onTrainingFinished() {
        forkJoinPool.shutdown();
    }

    /** Whether to fork this node's children, per the node type's fork probability. */
    private boolean shouldFork(GameTreeNode node) {
        if (node.depth % forkEveryNDepth != 0 || node.subtreeSize <= noForkSubtreeSize) return false;
        double probability = node instanceof ActionNode ? forkProbAction : forkProbChance;
        if (probability == 1) return true;
        if (probability == 0) return false;
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    @Override
    protected float[][] evaluateChildren(
            int player,
            int iteration,
            GameTreeNode parent,
            GameTreeNode[] children,
            float[][][] childReachProbs,
            long[] childBoards,
            double[] roundDeals) {
        boolean fork = shouldFork(parent);

        CfrTask[] tasks = new CfrTask[children.length];
        for (int k = 0; k < children.length; k++) {
            if (children[k] == null) continue;
            tasks[k] = new CfrTask(player, children[k], childReachProbs[k], iteration, childBoards[k], roundDeals);
            if (fork) tasks[k].fork();
        }

        float[][] utilities = new float[children.length][];
        for (int k = 0; k < children.length; k++) {
            if (tasks[k] == null) continue;
            utilities[k] = fork ? tasks[k].join() : tasks[k].compute();
        }
        return utilities;
    }

    private final class CfrTask extends RecursiveTask<float[]> {

        private final int player;
        private final GameTreeNode node;
        private final float[][] reachProbs;
        private final int iteration;
        private final long board;
        private final double[] roundDeals;

        CfrTask(int player, GameTreeNode node, float[][] reachProbs, int iteration, long board, double[] roundDeals) {
            this.player = player;
            this.node = node;
            this.reachProbs = reachProbs;
            this.iteration = iteration;
            this.board = board;
            this.roundDeals = roundDeals;
        }

        @Override
        protected float[] compute() {
            return cfr(player, node, reachProbs, iteration, board, roundDeals);
        }
    }
}

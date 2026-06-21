package pokersolver.solver;

import static pokersolver.utils.JsonUtil.MAPPER;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.GameTreeNode;
import pokersolver.ranges.PrivateCards;
import tools.jackson.databind.node.ObjectNode;

/**
 * Parallel CFR+ solver using ForkJoin work-stealing. Shares the entire tree traversal with the
 * single-threaded solver via {@link AbstractCfrSolver}; the only difference is that children are
 * evaluated as forked {@link CfrTask}s instead of in-line recursion.
 */
public class ParallelCfrPlusSolver extends AbstractCfrSolver {

    ForkJoinPool forkJoinPool;
    int nthreads;
    double forkprobAction;
    double forkprobChance;
    int forkEveryNDepth;
    int noForkSubtreeSize;

    public ParallelCfrPlusSolver(
            SolverConfig config,
            int nthreads,
            double forkprobAction,
            double forkprobChance,
            int forkBetween,
            int noForkSubtreeSize) {
        super(config);
        if (nthreads >= 1) {
            this.nthreads = nthreads;
        } else if (nthreads == -1) {
            this.nthreads = Runtime.getRuntime().availableProcessors();
        } else {
            throw new RuntimeException("nthread not correct");
        }
        this.forkJoinPool = new ForkJoinPool(this.nthreads);
        if (forkprobAction > 1 || forkprobAction < 0)
            throw new RuntimeException(String.format("forkprob action not between [0,1] : %s", forkprobAction));
        if (forkprobChance > 1 || forkprobChance < 0)
            throw new RuntimeException(String.format("forkprob chance not between [0,1] : %s", forkprobChance));
        this.forkprobAction = forkprobAction;
        this.forkprobChance = forkprobChance;
        this.forkEveryNDepth = forkBetween;
        // Forking at every action node measures fastest: each task does O(actions × hands) float
        // work, well above ForkJoin's per-task overhead, and full forking gives the best load
        // balance. A subtree-size cutoff (e.g. 64) measured ~6x slower on river trees.
        this.noForkSubtreeSize = noForkSubtreeSize;
        System.out.println(String.format("Using %s threads", this.nthreads));
    }

    @Override
    public void train() throws Exception {
        setTrainable(tree.getRoot());

        PrivateCards[][] playerPrivates = new PrivateCards[this.playerNumber][];
        playerPrivates[0] = pcm.getPreflopCards(0);
        playerPrivates[1] = pcm.getPreflopCards(1);

        BestResponse br = new BestResponse(
                playerPrivates, this.playerNumber, this.compairer, this.pcm, this.rrm, this.deck, this.debug);

        br.printExploitability(tree.getRoot(), 0, (float) tree.getRoot().getPot(), initialBoardLong);

        float[][] reachProbs = this.getReachProbs();

        long begintime = System.currentTimeMillis();
        long endtime = System.currentTimeMillis();

        try (Writer fileWriter = this.logfile != null
                ? Files.newBufferedWriter(Paths.get(this.logfile), StandardCharsets.UTF_8)
                : Writer.nullWriter()) {
            for (int i = 0; i < this.iterationNumber && !this.stopRequested; i++) {
                for (int playerId = 0; playerId < this.playerNumber; playerId++) {
                    if (this.debug) {
                        System.out.println(String.format(
                                "---------------------------------     player %s --------------------------------",
                                playerId));
                    }
                    this.roundDeal = new int[] {-1, -1, -1, -1};
                    forkJoinPool.invoke(
                            new CfrTask(playerId, this.tree.getRoot(), reachProbs, i, this.initialBoardLong));
                }
                if (i % this.printInterval == 0) {
                    endtime = System.currentTimeMillis();
                    long timeMs = endtime - begintime;
                    System.out.println(String.format("time used: %.2fs", (float) timeMs / 1000));
                    System.out.println("-------------------");
                    float exploitability = br.printExploitability(
                            tree.getRoot(), i + 1, (float) tree.getRoot().getPot(), initialBoardLong);
                    ObjectNode jo = MAPPER.createObjectNode();
                    jo.put("iteration", i);
                    jo.put("exploitability", exploitability);
                    jo.put("time_ms", timeMs);
                    fileWriter.write(String.format("%s\n", jo.toString()));
                    this.progressListener.onProgress(i, exploitability, timeMs);
                    if (this.stopExploitability > 0 && exploitability < this.stopExploitability) break;
                }
            }
        }
        endtime = System.currentTimeMillis();
        long timeMs = endtime - begintime;
        System.out.println("++++++++++++++++");
        System.out.println(String.format("solve finish, total time used: %.2fs", (float) timeMs / 1000));
        forkJoinPool.shutdown();
    }

    /** Whether to fork this node's children, mirroring the per-node-type fork probability. */
    private boolean shouldFork(GameTreeNode node) {
        double forkprob = (node instanceof ActionNode) ? this.forkprobAction : this.forkprobChance;
        boolean forkAt;
        if (forkprob == 1) {
            forkAt = true;
        } else if (forkprob == 0) {
            forkAt = false;
        } else {
            forkAt = Math.random() < forkprob;
        }
        if (node.depth % this.forkEveryNDepth != 0 || node.subtreeSize <= this.noForkSubtreeSize) {
            forkAt = false;
        }
        return forkAt;
    }

    @Override
    protected float[][] evaluateChildren(
            int player,
            int iter,
            GameTreeNode parent,
            GameTreeNode[] children,
            float[][][] childReachProbs,
            long[] childBoards) {
        boolean forkAt = shouldFork(parent);

        CfrTask[] tasks = new CfrTask[children.length];
        for (int k = 0; k < children.length; k++) {
            if (children[k] == null) continue;
            CfrTask task = new CfrTask(player, children[k], childReachProbs[k], iter, childBoards[k]);
            if (forkAt) task.fork();
            tasks[k] = task;
        }

        float[][] utilities = new float[children.length][];
        for (int k = 0; k < children.length; k++) {
            if (tasks[k] == null) continue;
            utilities[k] = forkAt ? tasks[k].join() : tasks[k].compute();
        }
        return utilities;
    }

    final class CfrTask extends RecursiveTask<float[]> {
        final int player;
        final GameTreeNode node;
        final float[][] reachProbs;
        final int iter;
        final long board;

        CfrTask(int player, GameTreeNode node, float[][] reachProbs, int iter, long board) {
            this.player = player;
            this.node = node;
            this.reachProbs = reachProbs;
            this.iter = iter;
            this.board = board;
        }

        @Override
        protected float[] compute() {
            return cfr(this.player, this.node, this.reachProbs, this.iter, this.board);
        }
    }
}

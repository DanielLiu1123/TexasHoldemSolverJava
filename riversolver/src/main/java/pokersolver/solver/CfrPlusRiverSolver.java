package pokersolver.solver;

import pokersolver.nodes.GameTreeNode;

/**
 * Single-threaded CFR solver: walks each node's children in order.
 *
 * <p>Deterministic under {@link MonteCarloAlg#NONE}, which makes it the reference the strategy
 * regression suite pins. The traversal and the training loop both live in {@link AbstractCfrSolver};
 * this class contributes only the scheduling discipline.
 */
public final class CfrPlusRiverSolver extends AbstractCfrSolver {

    public CfrPlusRiverSolver(SolverConfig config) {
        super(config);
    }

    @Override
    protected float[] traverse(
            int player, GameTreeNode root, float[][] reachProbs, int iteration, long board, double[] roundDeals) {
        return cfr(player, root, reachProbs, iteration, board, roundDeals);
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
        float[][] utilities = new float[children.length][];
        for (int k = 0; k < children.length; k++) {
            if (children[k] == null) continue;
            utilities[k] = cfr(player, children[k], childReachProbs[k], iteration, childBoards[k], roundDeals);
        }
        return utilities;
    }
}

package pokersolver;

import java.io.File;
import java.util.Objects;
import pokersolver.solver.Algorithm;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.Solver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;

/** Scenarios the solver tests share: a game tree, a board, and two ranges. */
final class SolverFixture {

    private SolverFixture() {}

    /** Ten hands: pairs down to nines, plus the broadway combos this board interacts with. */
    static final String NARROW_RANGE = "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT";

    static final String WIDE_RANGE = "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,KQ,KJ,KT,QJ,QT,JT,98,87,76";

    /** A monotone-draw river: three diamonds, and a straight completed by any nine. */
    static final int[] RIVER_BOARD = board("Kd", "Jd", "Td", "7s", "8s");

    static final int[] TURN_BOARD = board("Kd", "Jd", "Td", "7s");

    /** A three-action river tree: check/bet, then call/raise/fold. */
    static final String RIVER_TREE = "gametree/simple_part_tree_depthinf.km";

    static final String TURN_TREE = "gametree/part_tree_turn_depthinf.km";

    static int[] board(String... cards) {
        int[] ints = new int[cards.length];
        for (int i = 0; i < cards.length; i++) ints[i] = Card.strCard2int(cards[i]);
        return ints;
    }

    /** A freshly built tree — training mutates the trainables attached to its action nodes. */
    static GameTree tree(String resource) {
        ClassLoader loader = SolverFixture.class.getClassLoader();
        String path = new File(Objects.requireNonNull(loader.getResource(resource), resource)
                        .getFile())
                .getAbsolutePath();
        return SolverEnvironment.gameTreeFromJson(path);
    }

    static SolverConfig.Builder builder(
            String treeResource, int[] board, String range, Algorithm algorithm, int iterations) {
        return SolverConfig.builder()
                .tree(tree(treeResource))
                .range1(PrivateRangeConverter.rangeStr2Cards(range, board))
                .range2(PrivateRangeConverter.rangeStr2Cards(range, board))
                .initialBoard(board)
                .iterationNumber(iterations)
                .printInterval(10)
                .algorithm(algorithm)
                .monteCarloAlg(MonteCarloAlg.NONE);
    }

    /** Runs a solve and returns the final exploitability the progress listener reported. */
    static float solveAndMeasure(SolverConfig.Builder builder, boolean parallel) throws Exception {
        float[] last = {Float.NaN};
        SolverConfig config = builder.progressListener(
                        (iteration, exploitability, elapsedMs) -> last[0] = exploitability)
                .build();
        Solver solver =
                parallel ? new ParallelCfrPlusSolver(config, 4, 1.0, 0.0, 1, 0) : new CfrPlusRiverSolver(config);
        solver.train();
        return last[0];
    }
}

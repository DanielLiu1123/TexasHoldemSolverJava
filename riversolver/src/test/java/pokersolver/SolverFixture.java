package pokersolver;

import java.io.File;
import java.util.Objects;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.Algorithm;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.Solver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;

/** Scenarios the solver tests share: a deck, a tree, a board, and two ranges. */
final class SolverFixture {

    private SolverFixture() {}

    static final String SHORT_DECK_RANGE = "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT";

    static final String WIDE_SHORT_DECK_RANGE = "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,KQ,KJ,KT,QJ,QT,JT,98,87,76";

    /** A dry-ish short-deck river: no flush possible, one straight out there. */
    static final int[] RIVER_BOARD = board("Kd", "Jd", "Td", "7s", "8s");

    static final int[] TURN_BOARD = board("Kd", "Jd", "Td", "7s");

    static int[] board(String... cards) {
        int[] ints = new int[cards.length];
        for (int i = 0; i < cards.length; i++) ints[i] = Card.strCard2int(cards[i]);
        return ints;
    }

    static Config config(String yaml) {
        ClassLoader loader = SolverFixture.class.getClassLoader();
        return new Config(
                new File(Objects.requireNonNull(loader.getResource(yaml), yaml).getFile()).getAbsolutePath());
    }

    static Config shortDeckRiver() {
        return config("yamls/rule_shortdeck_simple.yaml");
    }

    static Config shortDeckTurn() {
        return config("yamls/rule_shortdeck_turnsolver.yaml");
    }

    static Config holdemRiver() {
        return config("yamls/rule_holdem_simple.yaml");
    }

    /** A solver config for {@code algorithm} on a freshly built tree — training mutates the tree. */
    static SolverConfig.Builder builder(Config config, int[] board, String range, Algorithm algorithm, int iterations) {
        Deck deck = SolverEnvironment.deckFromConfig(config);
        PrivateCards[] range1 = PrivateRangeConverter.rangeStr2Cards(range, board);
        PrivateCards[] range2 = PrivateRangeConverter.rangeStr2Cards(range, board);
        return SolverConfig.builder()
                .tree(SolverEnvironment.gameTreeFromConfig(config, deck))
                .range1(range1)
                .range2(range2)
                .initialBoard(board)
                .deck(deck)
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

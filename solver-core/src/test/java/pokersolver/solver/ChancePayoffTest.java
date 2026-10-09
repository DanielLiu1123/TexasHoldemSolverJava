package pokersolver.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pokersolver.Card;
import pokersolver.GameTree;
import pokersolver.eval.HandEvaluator;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.GameRound;
import pokersolver.nodes.TerminalNode;
import pokersolver.ranges.PrivateCards;

/** Direct enumeration of legal runouts, independent of the traversal's probability factors. */
class ChancePayoffTest {
    private static final PrivateCards HERO = hand("As", "Ah");
    private static final PrivateCards VILLAIN = hand("Ks", "Kh");

    private static PrivateCards hand(String first, String second) {
        return new PrivateCards(Card.strCard2int(first), Card.strCard2int(second), 1);
    }

    private static int[] board(int size) {
        return Stream.of("2c", "3d", "7h", "9s")
                .limit(size)
                .mapToInt(Card::strCard2int)
                .toArray();
    }

    private static SolverConfig.Builder scenario(int size) {
        var setting = new GameTreeBuildingSettings.StreetSetting(new float[0], new float[0], null, false);
        var settings = new GameTreeBuildingSettings(setting, setting, setting, setting, setting, setting);
        return SolverConfig.builder()
                .tree(new GameTree(10, 10, size - 1, 0, 0.5f, 1, 10, settings))
                .range1(new PrivateCards[] {HERO})
                .range2(new PrivateCards[] {VILLAIN})
                .initialBoard(board(size));
    }

    private static double exactEv(long board) {
        long unavailable = board | HERO.mask() | VILLAIN.mask();
        double total = 0;
        int runouts = 0;
        for (int card = 0; card < 52; card++) {
            if ((unavailable & (1L << card)) != 0) continue;
            if (Card.cardCount(board) == 4) {
                total += Integer.signum(HandEvaluator.compare(HERO.mask(), VILLAIN.mask(), board | (1L << card))) * 10;
                runouts++;
            } else {
                for (int river = card + 1; river < 52; river++) {
                    if ((unavailable & (1L << river)) != 0) continue;
                    total += Integer.signum(HandEvaluator.compare(
                                    HERO.mask(), VILLAIN.mask(), board | (1L << card) | (1L << river)))
                            * 10;
                    runouts++;
                }
            }
        }
        return total / runouts;
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void fullTraversalMatchesLegalRunoutEnumeration(int size) {
        var solver = new SequentialCfrSolver(scenario(size).build());
        var root = solver.getTree().getRoot();
        solver.setTrainable(root);
        float[] ev =
                solver.cfr(0, root, solver.getReachProbs(), 0, solver.initialBoardLong, AbstractCfrSolver.NO_SAMPLING);
        assertThat(ev[0]).isCloseTo((float) exactEv(solver.initialBoardLong), within(1e-4f));
    }

    @Test
    void meanOfEveryPublicSampleMatchesFullEnumeration() {
        var solver = new SequentialCfrSolver(
                scenario(4).monteCarloAlg(MonteCarloAlg.PUBLIC).build());
        var root = solver.getTree().getRoot();
        solver.setTrainable(root);
        int available = 52 - 4;
        double total = 0;
        for (int slot = 0; slot < available; slot++) {
            double[] deals = new double[4];
            deals[GameRound.RIVER.number() - 1] = (slot + 0.5) / available;
            total += solver.cfr(0, root, solver.getReachProbs(), 0, solver.initialBoardLong, deals)[0];
        }
        assertThat(total / available).isCloseTo(exactEv(solver.initialBoardLong), within(1e-5));
    }

    @Test
    void bestResponseUsesTheSameLegalRunoutProbabilities() {
        var solver = new SequentialCfrSolver(scenario(4).build());
        var root = (ActionNode) solver.getTree().getRoot();
        var check = root.getChildren().getFirst();
        root.setEdges(
                List.of(Action.CHECK, Action.FOLD),
                List.of(check, new TerminalNode(new double[] {0, 0}, 0, GameRound.TURN, 20, root)));
        solver.setTrainable(root);
        var response = new BestResponse(solver.ranges, 2, solver.privateCards, solver.riverRanges);
        // Villain can take the zero-payoff fold; hero receives half its showdown EV against
        // villain's initial uniform mix. Exploitability averages the two players' BR values.
        assertThat(response.exploitability(root, 20, solver.initialBoardLong))
                .isCloseTo((float) (exactEv(solver.initialBoardLong) / 4 / 20 * 100), within(1e-4f));
    }
}

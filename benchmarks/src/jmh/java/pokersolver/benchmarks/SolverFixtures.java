package pokersolver.benchmarks;

import java.util.Arrays;
import java.util.List;
import pokersolver.Card;
import pokersolver.Deck;
import pokersolver.GameTree;
import pokersolver.SolverEnvironment;
import pokersolver.solver.GameTreeBuildingSettings;

/**
 * Shared scenario fixtures for all benchmarks.
 *
 * <p>The solve scenario mirrors {@code benchmarks/benchmark_river.txt} / {@code
 * benchmark_turn.txt} (the historical piosolver comparison runs): pot 180, effective stacks 910,
 * 50%-pot bets and raises, all-in enabled on turn and river.
 */
final class SolverFixtures {

    private SolverFixtures() {}

    static final String IP_RANGE =
            "AA,KK,QQ,JJ,TT,99,88,77,66,55,44,33,22,AK,AQ,AJ,AT,A9,A8s,A8o:0.5,A7s,A6s,A5s,A4s,A3s,A2s,KQ,KJ,KT,"
                    + "K9s,K8s,K7s,K6s,K5s:0.5,K4s:0.5,K3s:0.2,QJ,QTs,QTo:0.5,Q9s,Q8s,Q7s:0.2,JT,J9s,J8s,T9s,T8s,"
                    + "T7s:0.5,98s,97s,87s,86s,76s,75s:0.5,65s,64s:0.5,54s,43s:0.5";

    static final String OOP_RANGE =
            "AA,KK,QQ,JJ,TT,99,88,77,66,55,44,33,22,AK,AQ,AJ,AT,A9s,A9o:0.2,A8s,A7s,A6s,A5s,A4s,A3s,A2s,KQ,KJ,KTs,"
                    + "KTo:0.2,K9s,K8s,K7s:0.5,K6s:0.5,K5s:0.2,QJs,QJo:0.2,QTs,Q9s,Q8s,Q7s:0.2,JTs,J9s,J8s,J7s:0.2,"
                    + "T9s,T8s,T7s,98s,97s,96s,87s,86s,85s:0.5,76s,75s,74s:0.5,65s,64s,63s:0.5,54s,53s,43s";

    static final String TURN_BOARD = "Qs,Jh,2h,2d";
    static final String RIVER_BOARD = "Qs,Jh,2h,2d,6c";

    static final float POT = 180.0f;
    static final float EFFECTIVE_STACK = 910.0f;

    /** Game-round encodings used by {@link GameTree}: flop=2, turn=3, river=4. */
    static final int ROUND_TURN = 3;

    static final int ROUND_RIVER = 4;

    static Deck holdemDeck() {
        List<String> ranks = Arrays.asList("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2");
        List<String> suits = Arrays.asList("h", "s", "d", "c");
        return new Deck(ranks, suits);
    }

    static int[] boardInts(String board) {
        return Arrays.stream(board.split(",")).mapToInt(Card::strCard2int).toArray();
    }

    /**
     * Builds a fresh game tree for the scenario. A new tree is needed per solve because training
     * mutates the trainables attached to its action nodes.
     */
    static GameTree buildTree(Deck deck, int round) {
        GameTreeBuildingSettings.StreetSetting noAllin =
                new GameTreeBuildingSettings.StreetSetting(new float[] {50.0f}, new float[] {50.0f}, null, false);
        GameTreeBuildingSettings.StreetSetting withAllin =
                new GameTreeBuildingSettings.StreetSetting(new float[] {50.0f}, new float[] {50.0f}, null, true);
        GameTreeBuildingSettings settings =
                new GameTreeBuildingSettings(noAllin, withAllin, withAllin, noAllin, withAllin, withAllin);
        return SolverEnvironment.gameTreeFromParams(
                deck, POT / 2, POT / 2, round, 5, 0.5f, 1.0f, EFFECTIVE_STACK + POT / 2, settings);
    }
}

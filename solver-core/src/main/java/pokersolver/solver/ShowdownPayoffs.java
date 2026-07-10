package pokersolver.solver;

import pokersolver.ranges.RiverRange;

/**
 * The showdown payoff kernel: given both players' ranges on a complete board, computes each of the
 * player's hands' expected payoff against the opponent's reach-weighted range.
 *
 * <p>Both ranges arrive sorted weakest-first, which lets two linear sweeps replace the quadratic
 * hand-versus-hand comparison. Sweeping up, a running total accumulates the opponent mass every
 * player hand beats; sweeping down, the mass it loses to. Ties fall in the gap between the two
 * sweeps and contribute nothing, which is what a chopped pot pays.
 *
 * <p>Card removal is folded into the same sweeps: {@code cardWinsum[c]} tracks how much of the
 * accumulated opponent mass holds card {@code c}, so subtracting the player's two cards removes
 * exactly the opponent combos that its own hole cards make impossible.
 *
 * <p>This is the inner loop shared by the CFR traversal ({@link AbstractCfrSolver#showdownUtility})
 * and the best-response evaluator ({@link BestResponse#showdownBestResponse}).
 */
final class ShowdownPayoffs {

    private static final int DECK_SIZE = 52;

    private ShowdownPayoffs() {}

    /**
     * Computes each of the player's hands' expected payoff against the opponent's weighted range.
     *
     * @param player the player's range on this board, weakest hand first
     * @param opponent the opponent's range on this board, weakest hand first
     * @param opponentReach opponent reach probabilities, indexed by {@link RiverRange#reachIndex()}
     * @param winPayoff the player's payoff when it wins the showdown
     * @param losePayoff the player's payoff when it loses (negative)
     * @param payoffsLength the size of the returned array — the player's unfiltered hand count
     * @return payoffs indexed by {@link RiverRange#reachIndex()}
     */
    static float[] compute(
            RiverRange player,
            RiverRange opponent,
            float[] opponentReach,
            float winPayoff,
            float losePayoff,
            int payoffsLength) {
        float[] payoffs = new float[payoffsLength];
        int[] playerRanks = player.ranks();
        int[] playerCard1 = player.card1();
        int[] playerCard2 = player.card2();
        int[] playerIndex = player.reachIndex();
        int[] oppoRanks = opponent.ranks();
        int[] oppoCard1 = opponent.card1();
        int[] oppoCard2 = opponent.card2();
        int[] oppoIndex = opponent.reachIndex();
        int oppoSize = oppoRanks.length;

        // Hands the player beats. Both ranges run weakest-first, so as the player hand strengthens
        // (rank falls) the opponent cursor only ever moves forward.
        float winSum = 0;
        float[] cardWinSum = new float[DECK_SIZE];
        int j = 0;
        for (int i = 0; i < playerRanks.length; i++) {
            int rank = playerRanks[i];
            while (j < oppoSize && rank < oppoRanks[j]) {
                float reach = opponentReach[oppoIndex[j]];
                winSum += reach;
                cardWinSum[oppoCard1[j]] += reach;
                cardWinSum[oppoCard2[j]] += reach;
                j++;
            }
            payoffs[playerIndex[i]] = (winSum - cardWinSum[playerCard1[i]] - cardWinSum[playerCard2[i]]) * winPayoff;
        }

        // Hands the player loses to: the mirror sweep, from the strongest hand down.
        float lossSum = 0;
        float[] cardLossSum = new float[DECK_SIZE];
        j = oppoSize - 1;
        for (int i = playerRanks.length - 1; i >= 0; i--) {
            int rank = playerRanks[i];
            while (j >= 0 && rank > oppoRanks[j]) {
                float reach = opponentReach[oppoIndex[j]];
                lossSum += reach;
                cardLossSum[oppoCard1[j]] += reach;
                cardLossSum[oppoCard2[j]] += reach;
                j--;
            }
            payoffs[playerIndex[i]] +=
                    (lossSum - cardLossSum[playerCard1[i]] - cardLossSum[playerCard2[i]]) * losePayoff;
        }
        return payoffs;
    }
}

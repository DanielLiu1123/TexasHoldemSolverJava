package pokersolver.solver;

import pokersolver.ranges.RiverCombs;

/**
 * The showdown payoff kernel: given both players' river combos sorted by hand strength, computes
 * each of the player's hands' expected payoff against the opponent's reach-weighted range.
 *
 * <p>Two ascending/descending sweeps over the rank-sorted combos accumulate the opponent mass the
 * player beats (win) and loses to (lose), subtracting the card-blocking contribution of the
 * player's own two cards. This is the inner loop shared by the CFR traversal ({@link
 * AbstractCfrSolver#showdownUtility}) and the best-response evaluator ({@link
 * BestResponse#showdownBestResponse}); it was previously copy-pasted, byte for byte, in both.
 */
final class ShowdownPayoffs {

    private ShowdownPayoffs() {}

    /**
     * @param playerCombs the player's river combos, ascending by rank
     * @param oppoCombs the opponent's river combos, ascending by rank
     * @param oppoReach opponent reach probabilities, indexed by {@link RiverCombs#reach_prob_index}
     * @param winPayoff the player's payoff when winning the showdown
     * @param losePayoff the player's payoff when losing the showdown
     * @param payoffsLength the size of the returned array (the player's hand count)
     * @return payoffs indexed by {@link RiverCombs#reach_prob_index}
     */
    static float[] compute(
            RiverCombs[] playerCombs,
            RiverCombs[] oppoCombs,
            float[] oppoReach,
            float winPayoff,
            float losePayoff,
            int payoffsLength) {
        float[] payoffs = new float[payoffsLength];

        // Hands the player beats: sweep opponents weaker than each player hand (ascending rank).
        float winsum = 0;
        float[] cardWinsum = new float[52];
        int j = 0;
        for (RiverCombs onePlayerComb : playerCombs) {
            while (j < oppoCombs.length && onePlayerComb.rank < oppoCombs[j].rank) {
                RiverCombs oneOppoComb = oppoCombs[j];
                winsum += oppoReach[oneOppoComb.reach_prob_index];
                cardWinsum[oneOppoComb.private_cards.card1] += oppoReach[oneOppoComb.reach_prob_index];
                cardWinsum[oneOppoComb.private_cards.card2] += oppoReach[oneOppoComb.reach_prob_index];
                j++;
            }
            payoffs[onePlayerComb.reach_prob_index] = (winsum
                            - cardWinsum[onePlayerComb.private_cards.card1]
                            - cardWinsum[onePlayerComb.private_cards.card2])
                    * winPayoff;
        }

        // Hands the player loses to: sweep opponents stronger than each player hand (descending).
        float losssum = 0;
        float[] cardLosssum = new float[52];
        j = oppoCombs.length - 1;
        for (int i = playerCombs.length - 1; i >= 0; i--) {
            RiverCombs onePlayerComb = playerCombs[i];
            while (j >= 0 && onePlayerComb.rank > oppoCombs[j].rank) {
                RiverCombs oneOppoComb = oppoCombs[j];
                losssum += oppoReach[oneOppoComb.reach_prob_index];
                cardLosssum[oneOppoComb.private_cards.card1] += oppoReach[oneOppoComb.reach_prob_index];
                cardLosssum[oneOppoComb.private_cards.card2] += oppoReach[oneOppoComb.reach_prob_index];
                j--;
            }
            payoffs[onePlayerComb.reach_prob_index] += (losssum
                            - cardLosssum[onePlayerComb.private_cards.card1]
                            - cardLosssum[onePlayerComb.private_cards.card2])
                    * losePayoff;
        }
        return payoffs;
    }
}

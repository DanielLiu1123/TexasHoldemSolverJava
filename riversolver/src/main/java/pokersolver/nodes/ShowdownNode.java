package pokersolver.nodes;

import org.jspecify.annotations.Nullable;

/** A terminal node where both players are still in and turn their cards over. */
public final class ShowdownNode extends GameTreeNode {

    private final double[] tiePayoffs;
    private final double[][] winnerPayoffs;

    public ShowdownNode(
            double[] tiePayoffs, double[][] winnerPayoffs, GameRound round, double pot, @Nullable GameTreeNode parent) {
        super(round, pot, parent);
        this.tiePayoffs = tiePayoffs;
        this.winnerPayoffs = winnerPayoffs;
    }

    /** Both players' payoffs when {@code winner} takes the pot. */
    public double[] payoffsIfWins(int winner) {
        return winnerPayoffs[winner];
    }

    /** Both players' payoffs when the pot is chopped. */
    public double[] tiePayoffs() {
        return tiePayoffs;
    }

    /** How many players this node pays out. */
    public int playerCount() {
        return tiePayoffs.length;
    }
}

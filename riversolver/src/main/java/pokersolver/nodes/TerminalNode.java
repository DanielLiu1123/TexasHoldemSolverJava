package pokersolver.nodes;

import org.jspecify.annotations.Nullable;

/** A terminal node reached by a fold: the player who did not fold takes the pot uncontested. */
public final class TerminalNode extends GameTreeNode {

    private final double[] payoffs;
    private final int winner;

    public TerminalNode(double[] payoffs, int winner, GameRound round, double pot, @Nullable GameTreeNode parent) {
        super(round, pot, parent);
        this.payoffs = payoffs;
        this.winner = winner;
    }

    /** Both players' payoffs. */
    public double[] getPayoffs() {
        return payoffs;
    }

    /** The player who did not fold. */
    public int winner() {
        return winner;
    }
}

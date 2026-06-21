package pokersolver.nodes;

import org.jspecify.annotations.Nullable;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contains implemtation for terminal node, Where all player(s) folds except one player take all.
 */
public class TerminalNode extends GameTreeNode {
    double[] payoffs;
    Integer winner;

    public TerminalNode(
            double[] payoffs, Integer winner, GameTreeNode.GameRound round, double pot, @Nullable GameTreeNode parent) {
        super(round, pot, parent);
        this.payoffs = payoffs;
        this.winner = winner;
    }

    public double[] getPayoffs() {
        return payoffs;
    }

    @Override
    public GameTreeNodeType getType() {
        return GameTreeNodeType.TERMINAL;
    }
}

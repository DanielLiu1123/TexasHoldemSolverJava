package pokersolver.nodes;

import org.jspecify.annotations.Nullable;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contains implemtation for showdown node, Where each remaining player show thrir holecard, winner take all.
 */
public class ShowdownNode extends GameTreeNode {

    double[] tiePayoffs;
    double[][] playerPayoffs;

    public ShowdownNode(
            double[] tiePayoffs, double[][] playerPayoffs, GameRound round, double pot, @Nullable GameTreeNode parent) {
        super(round, pot, parent);
        this.tiePayoffs = tiePayoffs;
        this.playerPayoffs = playerPayoffs;
    }

    public enum ShowDownResult {
        NOTTIE,
        TIE
    }

    public double[] getPayoffs(ShowDownResult result, @Nullable Integer winner) {
        if (result == ShowDownResult.NOTTIE) {
            if (winner == null) throw new RuntimeException("winner must not be null for NOTTIE");
            double[] retval = playerPayoffs[winner];
            assert (retval != null);
            return retval;
        } else {
            // (result == ShowDownResult.TIE)
            assert (winner == null);
            assert (tiePayoffs != null);
            return tiePayoffs;
        }
    }

    @Override
    public GameTreeNodeType getType() {
        return GameTreeNodeType.SHOWDOWN;
    }
}

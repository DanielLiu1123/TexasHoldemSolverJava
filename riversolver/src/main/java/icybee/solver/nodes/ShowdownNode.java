package icybee.solver.nodes;

import org.jspecify.annotations.Nullable;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contains implemtation for showdown node, Where each remaining player show thrir holecard, winner take all.
 */
public class ShowdownNode extends GameTreeNode {

    Double[] tie_payoffs;
    Double[][] player_payoffs;

    public ShowdownNode(
            Double[] tie_payoffs,
            Double[][] player_payoffs,
            GameRound round,
            Double pot,
            @Nullable GameTreeNode parent) {
        super(round, pot, parent);
        this.tie_payoffs = tie_payoffs;
        this.player_payoffs = player_payoffs;
    }

    public enum ShowDownResult {
        NOTTIE,
        TIE
    }

    public Double[] get_payoffs(ShowDownResult result, @Nullable Integer winner) {
        if (result == ShowDownResult.NOTTIE) {
            if (winner == null) throw new RuntimeException("winner must not be null for NOTTIE");
            Double[] retval = player_payoffs[winner];
            assert (retval != null);
            return retval;
        } else {
            // (result == ShowDownResult.TIE)
            assert (winner == null);
            assert (tie_payoffs != null);
            return tie_payoffs;
        }
    }

    @Override
    public GameTreeNodeType getType() {
        return GameTreeNodeType.SHOWDOWN;
    }
}

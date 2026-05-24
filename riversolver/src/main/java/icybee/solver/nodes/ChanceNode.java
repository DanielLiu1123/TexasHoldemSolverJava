package icybee.solver.nodes;

import icybee.solver.Card;
import icybee.solver.trainable.Trainable;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contians action node implementation
 */
public class ChanceNode extends GameTreeNode {
    // 如果一个chance node的game round是river，那么实际上它是一个介于turn和river之间的发牌节点
    List<GameTreeNode> children;

    @Nullable
    Trainable trainable;

    int player;

    List<Card> cards;

    boolean donk;

    public ChanceNode(
            @Nullable List<GameTreeNode> children,
            GameRound round,
            double pot,
            @Nullable GameTreeNode parent,
            List<Card> cards,
            boolean donk) {
        super(round, pot, parent);
        this.children = children != null ? children : new ArrayList<>();
        this.cards = cards;
        this.donk = donk;
    }

    public ChanceNode(
            @Nullable List<GameTreeNode> children,
            GameRound round,
            double pot,
            @Nullable GameTreeNode parent,
            List<Card> cards) {
        super(round, pot, parent);
        this.children = children != null ? children : new ArrayList<>();
        this.cards = cards;
        this.donk = false;
    }

    public List<Card> getCards() {
        return cards;
    }

    public List<GameTreeNode> getChildren() {
        return children;
    }

    public int getPlayer() {
        return player;
    }

    public @Nullable Trainable getTrainable() {
        return trainable;
    }

    public void setTrainable(Trainable trainable) {
        this.trainable = trainable;
    }

    public void setChildren(List<GameTreeNode> children) {
        this.children = children;
    }

    @Override
    public GameTreeNodeType getType() {
        return GameTreeNodeType.CHANCE;
    }

    public boolean isDonk() {
        return donk;
    }
}

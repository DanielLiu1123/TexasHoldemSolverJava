package pokersolver.nodes;

import pokersolver.trainable.Trainable;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contians action node implementation
 */
public class ActionNode extends GameTreeNode {

    List<GameActions> actions;
    List<GameTreeNode> children;

    @Nullable
    Trainable trainable;

    int player;

    public ActionNode(
            List<GameActions> actions,
            List<GameTreeNode> children,
            int player,
            GameRound round,
            double pot,
            @Nullable GameTreeNode parent) {
        super(round, pot, parent);
        assert (actions.size() == children.size());
        this.actions = actions;
        this.children = children;
        this.player = player;
    }

    public List<GameActions> getActions() {
        return actions;
    }

    public List<GameTreeNode> getChildren() {
        return children;
    }

    public void setActions(List<GameActions> actions) {
        this.actions = actions;
    }

    public void setChildren(List<GameTreeNode> children) {
        this.children = children;
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

    @Override
    public GameTreeNodeType getType() {
        return GameTreeNodeType.ACTION;
    }
}

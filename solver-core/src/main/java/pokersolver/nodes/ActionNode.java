package pokersolver.nodes;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pokersolver.trainable.Trainable;

/**
 * A node where one player chooses an action. Its {@link Trainable} holds the strategy CFR trains —
 * these are the only nodes that carry one.
 *
 * <p>{@code actions} and {@code children} are parallel: taking {@code actions.get(i)} leads to
 * {@code children.get(i)}. The tree builder fills both in after construction, because a child cannot
 * name its parent before the parent exists.
 */
public final class ActionNode extends GameTreeNode {

    private final int player;
    private List<Action> actions;
    private List<GameTreeNode> children;

    @Nullable
    private Trainable trainable;

    public ActionNode(
            List<Action> actions,
            List<GameTreeNode> children,
            int player,
            GameRound round,
            double pot,
            @Nullable GameTreeNode parent) {
        super(round, pot, parent);
        if (actions.size() != children.size()) {
            throw new IllegalArgumentException("%d actions but %d children".formatted(actions.size(), children.size()));
        }
        this.actions = actions;
        this.children = children;
        this.player = player;
    }

    public List<Action> getActions() {
        return actions;
    }

    public List<GameTreeNode> getChildren() {
        return children;
    }

    /** Installs this node's edges. Both lists must have the same length. */
    public void setEdges(List<Action> actions, List<GameTreeNode> children) {
        if (actions.size() != children.size()) {
            throw new IllegalArgumentException("%d actions but %d children".formatted(actions.size(), children.size()));
        }
        this.actions = actions;
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
}

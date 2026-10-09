package pokersolver.nodes;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A node of the post-flop game tree.
 *
 * <p>The hierarchy is sealed, so every traversal is a {@code switch} the compiler checks for
 * exhaustiveness — no node-type tag, no downcast, and no unreachable {@code default} branch that
 * would silently swallow a fifth kind of node if one were ever added:
 *
 * <pre>{@code
 * float[] utility = switch (node) {
 *     case ActionNode action -> actionUtility(action, ...);
 *     case ChanceNode chance -> chanceUtility(chance, ...);
 *     case ShowdownNode showdown -> showdownUtility(showdown, ...);
 *     case TerminalNode terminal -> terminalUtility(terminal, ...);
 * };
 * }</pre>
 */
public abstract sealed class GameTreeNode permits ActionNode, ChanceNode, ShowdownNode, TerminalNode {

    private final GameRound round;
    private final double pot;

    @Nullable
    private GameTreeNode parent;

    /** Distance from the root; filled in by {@code GameTree} once the tree is built. */
    public int depth;

    /** Number of nodes in this node's subtree, including itself; filled in by {@code GameTree}. */
    public int subtreeSize;

    protected GameTreeNode(GameRound round, double pot, @Nullable GameTreeNode parent) {
        if (round == null) throw new IllegalArgumentException("round is null");
        this.round = round;
        this.pot = pot;
        this.parent = parent;
    }

    public GameRound getRound() {
        return round;
    }

    public double getPot() {
        return pot;
    }

    public @Nullable GameTreeNode getParent() {
        return parent;
    }

    public void setParent(@Nullable GameTreeNode parent) {
        this.parent = parent;
    }

    /** The path from the root to this node, as the edge labels taken to reach it. */
    public String history() {
        StringBuilder path = new StringBuilder();
        for (GameTreeNode node = this; node.parent != null; node = node.parent) {
            path.insert(0, " <- " + edgeLabel(node.parent, node));
        }
        return path.isEmpty() ? "(root)" : path.substring(4);
    }

    private static String edgeLabel(GameTreeNode parent, GameTreeNode child) {
        return switch (parent) {
            case ActionNode action -> {
                int index = indexOfIdentical(action.getChildren(), child);
                yield index < 0
                        ? "?"
                        : "player %d %s"
                                .formatted(
                                        action.getPlayer(),
                                        action.getActions().get(index).label());
            }
            case ChanceNode chance -> {
                int index = indexOfIdentical(chance.getChildren(), child);
                yield index < 0 ? "?" : "deal " + chance.getCards().get(index);
            }
            case ShowdownNode ignored -> "showdown";
            case TerminalNode ignored -> "terminal";
        };
    }

    private static int indexOfIdentical(List<GameTreeNode> children, GameTreeNode child) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == child) return i;
        }
        return -1;
    }
}

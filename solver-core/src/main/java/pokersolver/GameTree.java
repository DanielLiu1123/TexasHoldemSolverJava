package pokersolver;

import java.io.IOException;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import pokersolver.solver.GameTreeBuildingSettings;
import tools.jackson.databind.node.ObjectNode;

/**
 * The post-flop game tree: every line the two players can take from the current spot to the end of
 * the hand. The solver's input and its output — a strategy at every action node — both hang off it.
 */
public final class GameTree {

    private final GameTreeNode root;

    /** Loads a tree from a JSON description. */
    public GameTree(String treeJsonPath) throws IOException {
        this(GameTreeJsonLoader.load(treeJsonPath));
    }

    /** Builds a tree from the betting rules. */
    public GameTree(
            float oopCommit,
            float ipCommit,
            int currentRound,
            int raiseLimit,
            float smallBlind,
            float bigBlind,
            float stack,
            GameTreeBuildingSettings buildingSettings) {
        this(GameTreeBuilder.build(
                oopCommit, ipCommit, currentRound, raiseLimit, smallBlind, bigBlind, stack, buildingSettings));
    }

    private GameTree(GameTreeNode root) {
        this.root = root;
        measure(root, 0);
    }

    public GameTreeNode getRoot() {
        return root;
    }

    /** Records each node's depth and subtree size — the parallel solver's forking heuristic reads both. */
    private static int measure(GameTreeNode node, int depth) {
        node.depth = depth;
        node.subtreeSize = switch (node) {
            case ActionNode action -> {
                int size = 1;
                for (GameTreeNode child : action.getChildren()) size += measure(child, depth + 1);
                yield size;
            }
            case ChanceNode chance -> {
                int size = 1;
                for (GameTreeNode child : chance.getChildren()) size += measure(child, depth + 1);
                yield size;
            }
            case ShowdownNode ignored -> 1;
            case TerminalNode ignored -> 1;
        };
        return node.subtreeSize;
    }

    /** The whole tree's strategy as JSON. Enormous for anything but a river solve — a flop tree runs to ~1 GB. */
    public ObjectNode dumps() {
        return GameTreeSerializer.dumps(root);
    }
}

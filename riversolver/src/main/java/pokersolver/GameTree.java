package pokersolver;

import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameActions;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import pokersolver.solver.GameTreeBuildingSettings;
import tools.jackson.databind.node.ObjectNode;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contains code for GameTree construction
 */
public class GameTree {
    @Nullable
    String treeJsonDir;

    GameTreeNode root;
    Deck deck;

    public Deck getDeck() {
        return deck;
    }

    public GameTreeNode getRoot() {
        return root;
    }

    public GameTree(String treeJsonDir, Deck deck) throws IOException {
        this.treeJsonDir = treeJsonDir;
        this.deck = deck;
        this.root = GameTreeJsonLoader.load(treeJsonDir, deck);
        recurrentSetDepth(this.root, 0);
    }

    public GameTree(
            Deck deck,
            float oopCommit,
            float ipCommit,
            int currentRound,
            int raiseLimit,
            float smallBlind,
            float bigBlind,
            float stack,
            GameTreeBuildingSettings buildingSettings)
            throws IOException {
        this.deck = deck;
        this.root = GameTreeBuilder.build(
                deck, oopCommit, ipCommit, currentRound, raiseLimit, smallBlind, bigBlind, stack, buildingSettings);
        recurrentSetDepth(this.root, 0);
    }

    int recurrentSetDepth(GameTreeNode node, int depth) {
        node.depth = depth;
        switch (node) {
            case ActionNode actionNode -> {
                int subtreeSize = 1;
                for (GameTreeNode oneChild : actionNode.getChildren()) {
                    subtreeSize += this.recurrentSetDepth(oneChild, depth + 1);
                }
                node.subtreeSize = subtreeSize;
            }
            case ChanceNode chanceNode -> {
                int subtreeSize = 1;
                for (GameTreeNode oneChild : chanceNode.getChildren()) {
                    subtreeSize += this.recurrentSetDepth(oneChild, depth + 1);
                }
                node.subtreeSize = subtreeSize;
            }
            default -> node.subtreeSize = 1;
        }
        return node.subtreeSize;
    }

    void recurrentPrintTree(GameTreeNode node, int depth, int depthLimit) throws ClassCastException {
        if (depthLimit != -1 && depth >= depthLimit) {
            return;
        }

        switch (node) {
            case ActionNode actionNode -> {
                List<GameTreeNode> children = actionNode.getChildren();
                List<GameActions> actions = actionNode.getActions();

                for (int i = 0; i < children.size(); i++) {
                    GameTreeNode oneChild = children.get(i);
                    GameActions oneAction = actions.get(i);

                    StringBuilder prefix = new StringBuilder();
                    prefix.repeat("\t", Math.max(0, depth));
                    System.out.printf("%sp%s: %s%n", prefix, actionNode.getPlayer(), oneAction.toString());
                    recurrentPrintTree(oneChild, depth + 1, depthLimit);
                }
            }
            case ChanceNode chanceNode -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%sCHANCE%n", prefix);
                recurrentPrintTree(chanceNode.getChildren().getFirst(), depth + 1, depthLimit);
            }
            case ShowdownNode showdownNode -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%s SHOWDOWN pot %f %n", prefix.toString(), showdownNode.getPot());

                prefix.append("\t");
                for (int i = 0; i < showdownNode.getPayoffs(ShowdownNode.ShowDownResult.TIE, null).length; i++) {
                    System.out.printf("%sif player %d wins, payoff :", prefix.toString(), i);
                    double[] payoffs = showdownNode.getPayoffs(ShowdownNode.ShowDownResult.NOTTIE, i);

                    for (int playerId = 0; playerId < payoffs.length; playerId++) {
                        System.out.printf(" p%d %f ", playerId, payoffs[playerId]);
                    }
                    System.out.println();
                }
                System.out.printf("%sif Tie, payoff :", prefix.toString());
                double[] payoffs = showdownNode.getPayoffs(ShowdownNode.ShowDownResult.TIE, null);

                for (int playerId = 0; playerId < payoffs.length; playerId++) {
                    System.out.printf(" p%d %f ", playerId, payoffs[playerId]);
                }
                System.out.println();
            }
            case TerminalNode terminalNode -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%s TERMINAL pot %f %n", prefix, terminalNode.getPot());

                prefix.append("\t");
                System.out.printf("%sTerminal payoff :", prefix);
                double[] payoffs = terminalNode.getPayoffs();

                for (int playerId = 0; playerId < payoffs.length; playerId++) {
                    System.out.printf(" p%d %f ", playerId, payoffs[playerId]);
                }
                System.out.println();
            }
            case null, default -> {}
        }
    }

    public void printTree(int depth) {
        if (depth < -1 || depth == 0) {
            throw new RuntimeException("depth can only be -1 or positive");
        }
        recurrentPrintTree(this.root, 0, depth);
    }

    public ObjectNode dumps(boolean withStatus) {
        if (withStatus) throw new RuntimeException();
        return GameTreeSerializer.dumps(this.root);
    }
}

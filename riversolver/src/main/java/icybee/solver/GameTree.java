package icybee.solver;

import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.ChanceNode;
import icybee.solver.nodes.GameActions;
import icybee.solver.nodes.GameTreeNode;
import icybee.solver.nodes.ShowdownNode;
import icybee.solver.nodes.TerminalNode;
import icybee.solver.solver.GameTreeBuildingSettings;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contains code for GameTree construction
 */
public class GameTree {
    @Nullable
    String tree_json_dir;

    GameTreeNode root;
    Deck deck;

    public Deck getDeck() {
        return deck;
    }

    public GameTreeNode getRoot() {
        return root;
    }

    public GameTree(String tree_json_dir, Deck deck) throws IOException {
        this.tree_json_dir = tree_json_dir;
        this.deck = deck;
        this.root = GameTreeJsonLoader.load(tree_json_dir, deck);
        recurrentSetDepth(this.root, 0);
    }

    public GameTree(
            Deck deck,
            float oop_commit,
            float ip_commit,
            int current_round,
            int raise_limit,
            float small_blind,
            float big_blind,
            float stack,
            GameTreeBuildingSettings buildingSettings)
            throws IOException {
        this.deck = deck;
        this.root = GameTreeBuilder.build(
                deck,
                oop_commit,
                ip_commit,
                current_round,
                raise_limit,
                small_blind,
                big_blind,
                stack,
                buildingSettings);
        recurrentSetDepth(this.root, 0);
    }

    int recurrentSetDepth(GameTreeNode node, int depth) {
        node.depth = depth;
        switch (node) {
            case ActionNode actionNode -> {
                int subtree_size = 1;
                for (GameTreeNode one_child : actionNode.getChildrens()) {
                    subtree_size += this.recurrentSetDepth(one_child, depth + 1);
                }
                node.subtree_size = subtree_size;
            }
            case ChanceNode chanceNode -> {
                int subtree_size = 1;
                for (GameTreeNode one_child : chanceNode.getChildrens()) {
                    subtree_size += this.recurrentSetDepth(one_child, depth + 1);
                }
                node.subtree_size = subtree_size;
            }
            default -> node.subtree_size = 1;
        }
        return node.subtree_size;
    }

    void recurrentPrintTree(GameTreeNode node, int depth, int depth_limit) throws ClassCastException {
        if (depth_limit != -1 && depth >= depth_limit) {
            return;
        }

        switch (node) {
            case ActionNode actionNode -> {
                List<GameTreeNode> childrens = actionNode.getChildrens();
                List<GameActions> actions = actionNode.getActions();

                for (int i = 0; i < childrens.size(); i++) {
                    GameTreeNode one_child = childrens.get(i);
                    GameActions one_action = actions.get(i);

                    StringBuilder prefix = new StringBuilder();
                    prefix.repeat("\t", Math.max(0, depth));
                    System.out.printf("%sp%s: %s%n", prefix, actionNode.getPlayer(), one_action.toString());
                    recurrentPrintTree(one_child, depth + 1, depth_limit);
                }
            }
            case ChanceNode chanceNode -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%sCHANCE%n", prefix);
                recurrentPrintTree(chanceNode.getChildrens().getFirst(), depth + 1, depth_limit);
            }
            case ShowdownNode showdown_node -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%s SHOWDOWN pot %f %n", prefix.toString(), showdown_node.getPot());

                prefix.append("\t");
                for (int i = 0; i < showdown_node.get_payoffs(ShowdownNode.ShowDownResult.TIE, null).length; i++) {
                    System.out.printf("%sif player %d wins, payoff :", prefix.toString(), i);
                    Double[] payoffs = showdown_node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, i);

                    for (int player_id = 0; player_id < payoffs.length; player_id++) {
                        System.out.printf(" p%d %f ", player_id, payoffs[player_id]);
                    }
                    System.out.println();
                }
                System.out.printf("%sif Tie, payoff :", prefix.toString());
                Double[] payoffs = showdown_node.get_payoffs(ShowdownNode.ShowDownResult.TIE, null);

                for (int player_id = 0; player_id < payoffs.length; player_id++) {
                    System.out.printf(" p%d %f ", player_id, payoffs[player_id]);
                }
                System.out.println();
            }
            case TerminalNode terminal_node -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%s TERMINAL pot %f %n", prefix, terminal_node.getPot());

                prefix.append("\t");
                System.out.printf("%sTerminal payoff :", prefix);
                Double[] payoffs = terminal_node.get_payoffs();

                for (int player_id = 0; player_id < payoffs.length; player_id++) {
                    System.out.printf(" p%d %f ", player_id, payoffs[player_id]);
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

    public ObjectNode dumps(boolean with_status) {
        if (with_status) throw new RuntimeException();
        return GameTreeSerializer.dumps(this.root);
    }
}

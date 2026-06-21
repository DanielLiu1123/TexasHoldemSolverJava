package pokersolver.nodes;

import org.jspecify.annotations.Nullable;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This class contains code of the game tree's node
 */
public abstract class GameTreeNode {
    public enum PokerActions {
        BEGIN,
        ROUNDBEGIN,
        BET,
        RAISE,
        CHECK,
        FOLD,
        CALL
    }

    public enum GameTreeNodeType {
        ACTION,
        SHOWDOWN,
        TERMINAL,
        CHANCE
    }

    public enum GameRound {
        PREFLOP,
        FLOP,
        TURN,
        RIVER;

        public static GameRound fromInt(int round) {
            return switch (round) {
                case 1 -> PREFLOP;
                case 2 -> FLOP;
                case 3 -> TURN;
                case 4 -> RIVER;
                default ->
                    throw new pokersolver.exceptions.RoundNotFoundException(String.format("round %s not found", round));
            };
        }

        public static GameRound fromString(String round) {
            return switch (round) {
                case "preflop" -> PREFLOP;
                case "flop" -> FLOP;
                case "turn" -> TURN;
                case "river" -> RIVER;
                default ->
                    throw new pokersolver.exceptions.RoundNotFoundException(String.format("round %s not found", round));
            };
        }
    }

    GameRound round;
    double pot;

    @Nullable
    GameTreeNode parent;

    public int depth;
    public int subtreeSize;

    public static String gameRound2String(GameRound gameRound) {
        if (gameRound == GameRound.PREFLOP) {
            return "preflop";
        } else if (gameRound == GameRound.FLOP) {
            return "flop";
        } else if (gameRound == GameRound.TURN) {
            return "turn";
        } else if (gameRound == GameRound.RIVER) {
            return "river";
        }
        throw new RuntimeException("round not found");
    }

    public static int gameRound2int(GameRound gameRound) {
        if (gameRound == GameRound.PREFLOP) {
            return 0;
        } else if (gameRound == GameRound.FLOP) {
            return 1;
        } else if (gameRound == GameRound.TURN) {
            return 2;
        } else if (gameRound == GameRound.RIVER) {
            return 3;
        }
        throw new RuntimeException("round not found");
    }

    public @Nullable GameTreeNode getParent() {
        return parent;
    }

    public void setParent(@Nullable GameTreeNode parent) {
        this.parent = parent;
    }

    public GameTreeNode(GameRound round, double pot, @Nullable GameTreeNode parent) {
        if (round == null) {
            throw new RuntimeException("round is null in GameTreeNode");
        }
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

    public void printHistory() {
        GameTreeNode.printNodeHistory(this);
    }

    public static void printNodeHistory(GameTreeNode node) {
        while (node != null) {
            GameTreeNode parentNode = node.parent;
            if (parentNode == null) break;
            if (parentNode instanceof ActionNode actionNode) {
                for (int i = 0; i < actionNode.getActions().size(); i++) {
                    if (actionNode.getChildren().get(i) == node) {
                        System.out.print(String.format(
                                "<- (player %s %s)",
                                actionNode.getPlayer(),
                                actionNode.getActions().get(i).toString()));
                    }
                }
            } else if (parentNode instanceof ChanceNode chanceNode) {
                for (int i = 0; i < chanceNode.getChildren().size(); i++) {
                    if (chanceNode.getChildren().get(i) == node) {
                        System.out.print(String.format(
                                "<- (deal card %s)",
                                chanceNode.getCards().get(i).toString()));
                    }
                }

            } else {
                System.out.print(String.format("<- (%s)", node.toString()));
            }
            node = parentNode;
        }
        System.out.println();
    }

    public abstract GameTreeNodeType getType();
}

package pokersolver;

import java.util.ArrayList;
import java.util.List;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameRound;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import pokersolver.solver.GameTreeBuildingSettings;

/** Builds a game tree from the betting rules, one node at a time, depth first. */
final class GameTreeBuilder {

    private GameTreeBuilder() {}

    /** The betting state at a node: what each player has put in, and what the rules allow next. */
    private record Rule(
            Deck deck,
            float oopCommit,
            float ipCommit,
            int currentRound,
            int raiseLimit,
            float smallBlind,
            float bigBlind,
            float stack,
            GameTreeBuildingSettings buildSettings) {

        float pot() {
            return oopCommit + ipCommit;
        }

        float commit(int player) {
            return switch (player) {
                case 0 -> ipCommit;
                case 1 -> oopCommit;
                default -> throw new IllegalArgumentException("unknown player: " + player);
            };
        }

        /** The state after {@code player} adds {@code amount} to their commit. */
        Rule withCommit(int player, double amount) {
            return switch (player) {
                case 0 ->
                    new Rule(
                            deck,
                            oopCommit,
                            (float) (ipCommit + amount),
                            currentRound,
                            raiseLimit,
                            smallBlind,
                            bigBlind,
                            stack,
                            buildSettings);
                case 1 ->
                    new Rule(
                            deck,
                            (float) (oopCommit + amount),
                            ipCommit,
                            currentRound,
                            raiseLimit,
                            smallBlind,
                            bigBlind,
                            stack,
                            buildSettings);
                default -> throw new IllegalArgumentException("unknown player: " + player);
            };
        }

        Rule nextRound() {
            return new Rule(
                    deck,
                    oopCommit,
                    ipCommit,
                    currentRound + 1,
                    raiseLimit,
                    smallBlind,
                    bigBlind,
                    stack,
                    buildSettings);
        }
    }

    /** The action that led to the node being expanded — it decides what may be played there. */
    private enum LastAction {
        ROUND_BEGIN,
        BEGIN,
        BET,
        RAISE,
        CHECK,
        FOLD,
        CALL
    }

    static GameTreeNode build(
            Deck deck,
            float oopCommit,
            float ipCommit,
            int currentRound,
            int raiseLimit,
            float smallBlind,
            float bigBlind,
            float stack,
            GameTreeBuildingSettings buildSettings) {
        Rule rule = new Rule(
                deck, oopCommit, ipCommit, currentRound, raiseLimit, smallBlind, bigBlind, stack, buildSettings);
        ActionNode root = new ActionNode(List.of(), List.of(), 1, GameRound.fromInt(currentRound), rule.pot(), null);
        expand(root, rule, LastAction.ROUND_BEGIN, 0, 0);
        return root;
    }

    private static void expand(GameTreeNode node, Rule rule, LastAction lastAction, int checkTimes, int raiseTimes) {
        switch (node) {
            case ActionNode action -> expandAction(action, rule, lastAction, checkTimes, raiseTimes);
            case ChanceNode chance -> expandChance(chance, rule);
            case ShowdownNode ignored -> {}
            case TerminalNode ignored -> {}
        }
    }

    private static void expandChance(ChanceNode root, Rule rule) {
        List<GameTreeNode> children = new ArrayList<>(rule.deck().getCards().size());
        boolean bothAllIn = rule.oopCommit() == rule.ipCommit() && rule.oopCommit() == rule.stack();

        for (int i = 0; i < rule.deck().getCards().size(); i++) {
            GameTreeNode child;
            Rule childRule;
            if (bothAllIn && rule.currentRound() >= GameRound.RIVER.number()) {
                child = showdown(rule, root);
                childRule = rule;
            } else if (bothAllIn) {
                childRule = rule.nextRound();
                child = new ChanceNode(
                        List.of(),
                        GameRound.fromInt(rule.currentRound() + 1),
                        rule.pot(),
                        root,
                        rule.deck().getCards());
            } else {
                childRule = rule;
                child = new ActionNode(
                        List.of(), List.of(), 1, GameRound.fromInt(rule.currentRound()), rule.pot(), root);
            }
            children.add(child);
            expand(child, childRule, LastAction.BEGIN, 0, 0);
        }
        root.setChildren(children);
    }

    private static void expandAction(
            ActionNode root, Rule rule, LastAction lastAction, int checkTimes, int raiseTimes) {
        if (lastAction == LastAction.FOLD) return;

        int player = root.getPlayer();
        int nextPlayer = 1 - player;
        List<Action> actions = new ArrayList<>();
        List<GameTreeNode> children = new ArrayList<>();

        switch (lastAction) {
            case ROUND_BEGIN, BEGIN -> {
                addCheck(root, rule, lastAction, checkTimes, nextPlayer, actions, children);
                addBets(root, rule, player, nextPlayer, actions, children);
            }
            case BET, RAISE -> {
                addCall(root, rule, player, nextPlayer, actions, children);
                addRaises(root, rule, lastAction, player, nextPlayer, raiseTimes, actions, children);
                addFold(root, rule, player, nextPlayer, actions, children);
            }
            case CHECK -> {
                addCheck(root, rule, lastAction, checkTimes, nextPlayer, actions, children);
                addRaises(root, rule, lastAction, player, nextPlayer, raiseTimes, actions, children);
                addBets(root, rule, player, nextPlayer, actions, children);
            }
            case CALL -> {
                addCheck(root, rule, lastAction, checkTimes, nextPlayer, actions, children);
                addRaises(root, rule, lastAction, player, nextPlayer, raiseTimes, actions, children);
            }
            case FOLD -> throw new IllegalStateException("unreachable");
        }

        if (actions.isEmpty()) throw new IllegalStateException("action node with no legal actions");
        root.setEdges(actions, children);
    }

    /** A check either passes to the opponent, or — if it closes the round — deals or shows down. */
    private static void addCheck(
            ActionNode root,
            Rule rule,
            LastAction lastAction,
            int checkTimes,
            int nextPlayer,
            List<Action> actions,
            List<GameTreeNode> children) {
        boolean closesRound = checkTimes >= 1 || (lastAction == LastAction.CALL && isRootChild(root));

        GameTreeNode next;
        Rule nextRule;
        if (closesRound && rule.currentRound() == GameRound.RIVER.number()) {
            next = showdown(rule, root);
            nextRule = rule;
        } else if (closesRound) {
            nextRule = rule.nextRound();
            next = new ChanceNode(
                    List.of(),
                    GameRound.fromInt(rule.currentRound() + 1),
                    rule.pot(),
                    root,
                    rule.deck().getCards());
        } else {
            nextRule = rule;
            next = new ActionNode(
                    List.of(), List.of(), nextPlayer, GameRound.fromInt(rule.currentRound()), rule.pot(), root);
        }
        expand(next, nextRule, LastAction.CHECK, checkTimes + 1, 0);
        actions.add(Action.CHECK);
        children.add(next);
    }

    /** An opening bet, or — for the out-of-position player after they closed the last round — a donk bet. */
    private static void addBets(
            ActionNode root, Rule rule, int player, int nextPlayer, List<Action> actions, List<GameTreeNode> children) {
        BetSizing.BetType betType =
                root.getPlayer() == 1 && root.getParent() instanceof ChanceNode chance && chance.isDonk()
                        ? BetSizing.BetType.DONK
                        : BetSizing.BetType.BET;

        for (double amount : possibleBets(root, player, nextPlayer, rule, betType)) {
            Rule nextRule = rule.withCommit(player, amount);
            GameTreeNode next = new ActionNode(
                    List.of(), List.of(), nextPlayer, GameRound.fromInt(rule.currentRound()), rule.pot(), root);
            expand(next, nextRule, LastAction.BET, 0, 0);
            actions.add(new Action.Bet(amount));
            children.add(next);
        }
    }

    /** A call either closes the round — dealing or showing down — or, at the root, passes the action. */
    private static void addCall(
            ActionNode root, Rule rule, int player, int nextPlayer, List<Action> actions, List<GameTreeNode> children) {
        Rule nextRule = rule.withCommit(player, rule.commit(nextPlayer) - rule.commit(player));

        GameTreeNode next;
        if (root.getParent() == null) {
            next = new ActionNode(
                    List.of(), List.of(), nextPlayer, GameRound.fromInt(rule.currentRound()), rule.pot(), root);
        } else if (rule.currentRound() == GameRound.RIVER.number()) {
            next = showdown(nextRule, root);
        } else {
            // Out of position closing the round with a call may lead into the next one.
            next = new ChanceNode(
                    List.of(),
                    GameRound.fromInt(rule.currentRound() + 1),
                    rule.pot(),
                    root,
                    rule.deck().getCards(),
                    player == 1);
            nextRule = nextRule.nextRound();
        }
        expand(next, nextRule, LastAction.CALL, 0, 0);
        actions.add(Action.CALL);
        children.add(next);
    }

    private static void addRaises(
            ActionNode root,
            Rule rule,
            LastAction lastAction,
            int player,
            int nextPlayer,
            int raiseTimes,
            List<Action> actions,
            List<GameTreeNode> children) {
        // A raise after a call is only the big blind's option preflop; a raise after a check is only
        // a check-raise, which needs a bet to raise over.
        if (lastAction == LastAction.CALL && !isRootChild(root)) return;
        if (lastAction == LastAction.CHECK && !(isRootChild(root) && rule.currentRound() == GameRound.PREFLOP.number()))
            return;
        if (raiseTimes >= rule.raiseLimit()) return;

        for (double amount : possibleBets(root, player, nextPlayer, rule, BetSizing.BetType.RAISE)) {
            Rule nextRule = rule.withCommit(player, amount);
            GameTreeNode next = new ActionNode(
                    List.of(), List.of(), nextPlayer, GameRound.fromInt(rule.currentRound()), rule.pot(), root);
            expand(next, nextRule, LastAction.RAISE, 0, raiseTimes + 1);
            actions.add(new Action.Raise(amount));
            children.add(next);
        }
    }

    private static void addFold(
            ActionNode root, Rule rule, int player, int nextPlayer, List<Action> actions, List<GameTreeNode> children) {
        float forfeited = rule.commit(player);
        double[] payoffs = player == 0 ? new double[] {-forfeited, forfeited} : new double[] {forfeited, -forfeited};
        GameTreeNode next =
                new TerminalNode(payoffs, nextPlayer, GameRound.fromInt(rule.currentRound()), rule.pot(), root);
        expand(next, rule, LastAction.FOLD, 0, 0);
        actions.add(Action.FOLD);
        children.add(next);
    }

    private static ShowdownNode showdown(Rule rule, GameTreeNode parent) {
        double ipCommit = rule.ipCommit();
        double oopCommit = rule.oopCommit();
        double chopped = (ipCommit + oopCommit) / 2;
        double[][] winnerPayoffs = {
            {oopCommit, -oopCommit},
            {-ipCommit, ipCommit}
        };
        double[] tiePayoffs = {chopped - ipCommit, chopped - oopCommit};
        return new ShowdownNode(tiePayoffs, winnerPayoffs, GameRound.fromInt(rule.currentRound()), rule.pot(), parent);
    }

    /** Whether {@code node}'s parent is the tree's root — i.e. this is the first decision of the solve. */
    private static boolean isRootChild(GameTreeNode node) {
        GameTreeNode parent = node.getParent();
        return parent != null && parent.getParent() == null;
    }

    private static List<Double> possibleBets(
            GameTreeNode root, int player, int nextPlayer, Rule rule, BetSizing.BetType betType) {
        return BetSizing.possibleBets(
                player,
                nextPlayer,
                rule.ipCommit(),
                rule.oopCommit(),
                rule.smallBlind(),
                rule.bigBlind(),
                rule.stack(),
                rule.buildSettings().getSettings(root.getRound(), player),
                betType);
    }
}

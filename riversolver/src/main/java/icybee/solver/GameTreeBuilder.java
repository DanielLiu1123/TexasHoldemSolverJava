package icybee.solver;

import icybee.solver.exceptions.NodeNotFoundException;
import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.ChanceNode;
import icybee.solver.nodes.GameActions;
import icybee.solver.nodes.GameTreeNode;
import icybee.solver.nodes.ShowdownNode;
import icybee.solver.nodes.TerminalNode;
import icybee.solver.solver.GameTreeBuildingSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Builds a GameTree programmatically from betting rules. */
class GameTreeBuilder {

    static final class Rule {
        Deck deck;
        float oopCommit;
        float ipCommit;
        int currentRound;
        int raiseLimit;
        float smallBlind;
        float bigBlind;
        float stack;
        GameTreeBuildingSettings buildSettings;

        Rule(
                Deck deck,
                float oopCommit,
                float ipCommit,
                int currentRound,
                int raiseLimit,
                float smallBlind,
                float bigBlind,
                float stack,
                GameTreeBuildingSettings buildSettings) {
            this.deck = deck;
            this.oopCommit = oopCommit;
            this.ipCommit = ipCommit;
            this.currentRound = currentRound;
            this.raiseLimit = raiseLimit;
            this.smallBlind = smallBlind;
            this.bigBlind = bigBlind;
            this.stack = stack;
            this.buildSettings = buildSettings;
        }

        Rule(Rule rule) {
            this.deck = rule.deck;
            this.oopCommit = rule.oopCommit;
            this.ipCommit = rule.ipCommit;
            this.currentRound = rule.currentRound;
            this.raiseLimit = rule.raiseLimit;
            this.smallBlind = rule.smallBlind;
            this.bigBlind = rule.bigBlind;
            this.stack = rule.stack;
            this.buildSettings = rule.buildSettings;
        }

        float getPot() {
            return this.oopCommit + this.ipCommit;
        }

        float getCommit(int player) {
            if (player == 0) return this.ipCommit;
            else if (player == 1) return this.oopCommit;
            else throw new RuntimeException("unknown player");
        }
    }

    enum BetType {
        BET,
        DONK,
        RAISE
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
        return buildTree(rule);
    }

    private static GameTreeNode buildTree(Rule rule) {
        GameTreeNode.GameRound round = GameTreeNode.GameRound.fromInt(rule.currentRound);
        ActionNode node = new ActionNode(List.of(), List.of(), 1, round, (double) rule.getPot(), null);
        buildRecursive(node, rule);
        return node;
    }

    private static GameTreeNode buildRecursive(GameTreeNode node, Rule rule) {
        return buildRecursive(node, rule, "roundbegin", 0, 0);
    }

    @SuppressWarnings("NullAway")
    private static GameTreeNode buildRecursive(
            GameTreeNode node, Rule rule, String lastAction, int checkTimes, int raiseTimes) {
        switch (node) {
            case ActionNode actionNode -> buildAction(actionNode, rule, lastAction, checkTimes, raiseTimes);
            case ShowdownNode _, TerminalNode _ -> {}
            case ChanceNode chanceNode -> buildChance(chanceNode, rule);
            case null, default -> throw new RuntimeException("node type unknown");
        }
        return node;
    }

    @SuppressWarnings("NullAway")
    private static void buildChance(ChanceNode root, Rule rule) {
        Rule nextRule = new Rule(rule);
        List<GameTreeNode> children = new ArrayList<>();
        assert rule.currentRound <= 4;
        for (Card ignored : rule.deck.getCards()) {
            GameTreeNode oneNode;
            if (rule.oopCommit == rule.ipCommit && rule.oopCommit == rule.stack) {
                if (rule.currentRound >= 4) {
                    double p1Commit = rule.ipCommit;
                    double p2Commit = rule.oopCommit;
                    double peaceGetback = (p1Commit + p2Commit) / 2;
                    double[][] payoffs = {
                        {p2Commit, -p2Commit},
                        {-p1Commit, p1Commit}
                    };
                    nextRule = new Rule(rule);
                    oneNode = new ShowdownNode(
                            new double[] {peaceGetback - p1Commit, peaceGetback - p2Commit},
                            payoffs,
                            GameTreeNode.GameRound.fromInt(rule.currentRound),
                            (double) rule.getPot(),
                            root);
                } else {
                    nextRule = new Rule(rule);
                    nextRule.currentRound += 1;
                    assert nextRule.currentRound <= 4;
                    oneNode = new ChanceNode(
                            null,
                            GameTreeNode.GameRound.fromInt(rule.currentRound + 1),
                            (double) rule.getPot(),
                            root,
                            rule.deck.getCards());
                }
            } else {
                oneNode = new ActionNode(
                        List.of(),
                        List.of(),
                        1,
                        GameTreeNode.GameRound.fromInt(rule.currentRound),
                        (double) rule.getPot(),
                        root);
            }
            children.add(oneNode);
            assert nextRule.currentRound <= 4;
            buildRecursive(oneNode, nextRule, "begin", 0, 0);
        }
        root.setChildren(children);
    }

    @SuppressWarnings("NullAway")
    private static void buildAction(ActionNode root, Rule rule, String lastAction, int checkTimes, int raiseTimes) {
        int player = root.getPlayer();

        String[] possibleActions =
                switch (lastAction) {
                    case "roundbegin", "begin" -> new String[] {"check", "bet"};
                    case "bet", "raise" -> new String[] {"call", "raise", "fold"};
                    case "check" -> new String[] {"check", "raise", "bet"};
                    case "fold" -> null;
                    case "call" -> new String[] {"check", "raise"};
                    default -> throw new NodeNotFoundException(String.format("last action %s not found", lastAction));
                };
        int nextPlayer = 1 - player;

        List<GameActions> actions = new ArrayList<>();
        List<GameTreeNode> children = new ArrayList<>();

        if (possibleActions == null) return;

        for (String action : possibleActions) {
            switch (action) {
                case "check" -> {
                    GameTreeNode nextNode;
                    Rule nextRule;
                    if ((Objects.equals(lastAction, "call")
                                    && root.getParent() != null
                                    && root.getParent().getParent() == null)
                            || checkTimes >= 1) {
                        if (rule.currentRound == 4) {
                            double p1Commit = rule.ipCommit;
                            double p2Commit = rule.oopCommit;
                            double peaceGetback = (p1Commit + p2Commit) / 2;
                            double[][] payoffs = {
                                {p2Commit, -p2Commit},
                                {-p1Commit, p1Commit}
                            };
                            nextRule = new Rule(rule);
                            nextNode = new ShowdownNode(
                                    new double[] {peaceGetback - p1Commit, peaceGetback - p2Commit},
                                    payoffs,
                                    GameTreeNode.GameRound.fromInt(rule.currentRound),
                                    (double) rule.getPot(),
                                    root);
                        } else {
                            nextRule = new Rule(rule);
                            nextRule.currentRound += 1;
                            nextNode = new ChanceNode(
                                    null,
                                    GameTreeNode.GameRound.fromInt(rule.currentRound + 1),
                                    (double) rule.getPot(),
                                    root,
                                    rule.deck.getCards());
                        }
                    } else {
                        nextRule = new Rule(rule);
                        nextNode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextPlayer,
                                GameTreeNode.GameRound.fromInt(rule.currentRound),
                                (double) rule.getPot(),
                                root);
                    }
                    buildRecursive(nextNode, nextRule, "check", checkTimes + 1, 0);
                    actions.add(new GameActions(GameTreeNode.PokerActions.CHECK, 0.0));
                    children.add(nextNode);
                }
                case "bet" -> {
                    BetType betType = BetType.BET;
                    if (root.getPlayer() == 1
                            && root.getParent() != null
                            && root.getParent() instanceof ChanceNode chanceNodeBeforeThis) {
                        if (chanceNodeBeforeThis.isDonk()) betType = BetType.DONK;
                    }
                    List<Double> betSizes = getPossibleBets(root, player, nextPlayer, rule, betType);
                    for (Double oneBettingSize : betSizes) {
                        Rule nextRule = new Rule(rule);
                        if (player == 0) nextRule.ipCommit = (float) (nextRule.ipCommit + oneBettingSize);
                        else if (player == 1) nextRule.oopCommit = (float) (nextRule.oopCommit + oneBettingSize);
                        else throw new RuntimeException("unknown player");
                        GameTreeNode nextNode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextPlayer,
                                GameTreeNode.GameRound.fromInt(rule.currentRound),
                                (double) rule.getPot(),
                                root);
                        buildRecursive(nextNode, nextRule, "bet", 0, 0);
                        actions.add(new GameActions(GameTreeNode.PokerActions.BET, oneBettingSize));
                        children.add(nextNode);
                    }
                }
                case "call" -> {
                    Rule nextRule = new Rule(rule);
                    if (player == 0) nextRule.ipCommit += (rule.oopCommit - rule.ipCommit);
                    else if (player == 1) nextRule.oopCommit += (rule.ipCommit - rule.oopCommit);
                    else throw new RuntimeException("unknown player");

                    GameTreeNode nextNode;
                    if (root.getParent() == null) {
                        nextNode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextPlayer,
                                GameTreeNode.GameRound.fromInt(rule.currentRound),
                                (double) rule.getPot(),
                                root);
                    } else if (rule.currentRound == 4) {
                        double p1Commit = nextRule.ipCommit;
                        double p2Commit = nextRule.oopCommit;
                        double peaceGetback = (p1Commit + p2Commit) / 2;
                        double[][] payoffs = {
                            {p2Commit, -p2Commit},
                            {-p1Commit, p1Commit}
                        };
                        double[] tiePayoffs = new double[] {peaceGetback - p1Commit, peaceGetback - p2Commit};
                        nextNode = new ShowdownNode(
                                tiePayoffs,
                                payoffs,
                                GameTreeNode.GameRound.fromInt(rule.currentRound),
                                (double) rule.getPot(),
                                root);
                    } else {
                        nextRule.currentRound += 1;
                        boolean donk = player == 1;
                        nextNode = new ChanceNode(
                                null,
                                GameTreeNode.GameRound.fromInt(rule.currentRound + 1),
                                (double) rule.getPot(),
                                root,
                                rule.deck.getCards(),
                                donk);
                    }
                    assert nextRule.currentRound <= 4;
                    buildRecursive(nextNode, nextRule, "call", 0, 0);
                    actions.add(new GameActions(
                            GameTreeNode.PokerActions.CALL, (double) Math.abs(rule.oopCommit - rule.ipCommit)));
                    children.add(nextNode);
                }
                case "raise" -> {
                    if (Objects.equals(lastAction, "call")) {
                        if (!(root.getParent() != null && root.getParent().getParent() == null)) continue;
                    } else if (Objects.equals(lastAction, "check")) {
                        if (!(root.getParent() != null
                                && root.getParent().getParent() == null
                                && rule.currentRound == 1)) continue;
                    }
                    if (raiseTimes >= rule.raiseLimit) continue;
                    List<Double> betSizes = getPossibleBets(root, player, nextPlayer, rule, BetType.RAISE);
                    for (Double oneBettingSize : betSizes) {
                        Rule nextRule = new Rule(rule);
                        if (player == 0) nextRule.ipCommit = (float) (nextRule.ipCommit + oneBettingSize);
                        else if (player == 1) nextRule.oopCommit = (float) (nextRule.oopCommit + oneBettingSize);
                        else throw new RuntimeException("unknown player");
                        GameTreeNode nextNode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextPlayer,
                                GameTreeNode.GameRound.fromInt(rule.currentRound),
                                (double) rule.getPot(),
                                root);
                        buildRecursive(nextNode, nextRule, "raise", 0, raiseTimes + 1);
                        actions.add(new GameActions(GameTreeNode.PokerActions.RAISE, oneBettingSize));
                        children.add(nextNode);
                    }
                }
                case "fold" -> {
                    Rule nextRule = new Rule(rule);
                    double[] payoffs;
                    if (player == 0) {
                        payoffs = new double[] {(double) -rule.ipCommit, (double) rule.ipCommit};
                    } else if (player == 1) {
                        payoffs = new double[] {(double) rule.oopCommit, (double) -rule.oopCommit};
                    } else throw new RuntimeException("unknown player");
                    GameTreeNode nextNode = new TerminalNode(
                            payoffs,
                            nextPlayer,
                            GameTreeNode.GameRound.fromInt(rule.currentRound),
                            (double) rule.getPot(),
                            root);
                    buildRecursive(nextNode, nextRule, "fold", 0, 0);
                    actions.add(new GameActions(GameTreeNode.PokerActions.FOLD, 0.0));
                    children.add(nextNode);
                }
                case null, default -> {}
            }
        }
        assert !actions.isEmpty();
        root.setActions(actions);
        root.setChildren(children);
    }

    private static double roundNearest(double number, double roundNum) {
        roundNum = 1 / roundNum;
        return Math.round(number * roundNum) / roundNum;
    }

    private static List<Double> getPossibleBets(
            GameTreeNode root, int player, int nextPlayer, Rule rule, BetType betType) {
        assert player == 1 - nextPlayer;
        GameTreeBuildingSettings.StreetSetting streetSetting = rule.buildSettings.getSettings(root.getRound(), player);
        ArrayList<Double> betsRatios = new ArrayList<>();
        boolean allIn = streetSetting.allin;
        float[] betsFromRule;
        if (betType == BetType.BET) betsFromRule = streetSetting.bet_sizes;
        else if (betType == BetType.DONK) {
            betsFromRule = streetSetting.donk_sizes;
            allIn = false;
        } else if (betType == BetType.RAISE) betsFromRule = streetSetting.raise_sizes;
        else throw new RuntimeException("bet type unknown");
        if (betsFromRule == null) return new ArrayList<>();
        for (float oneBet : betsFromRule) {
            betsRatios.add((double) oneBet / 100);
        }
        float pot = rule.ipCommit + rule.oopCommit;
        List<Double> possibleAmounts = new ArrayList<>();
        for (Double oneBet : betsRatios) {
            Double amount;
            if (rule.oopCommit == rule.smallBlind) {
                amount = oneBet * rule.bigBlind - rule.smallBlind;
                amount = roundNearest(amount, rule.smallBlind);
            } else if (rule.ipCommit == rule.bigBlind && rule.oopCommit == rule.bigBlind) {
                amount = oneBet * rule.bigBlind;
                amount = roundNearest(amount, rule.smallBlind);
            } else {
                amount = oneBet * pot;
                amount = roundNearest(amount, rule.bigBlind);
            }
            if (amount < rule.stack - rule.getCommit(player)) {
                possibleAmounts.add(amount);
            }
        }
        if (allIn) possibleAmounts.add((double) (rule.stack - rule.getCommit(player)));

        if (rule.getCommit(player) != rule.smallBlind) {
            possibleAmounts = possibleAmounts.stream()
                    .filter(e -> e > 0)
                    .map(n -> (double) n.intValue())
                    .collect(Collectors.toList());
        }
        if (rule.getCommit(player) == rule.smallBlind) {
            possibleAmounts =
                    possibleAmounts.stream().filter(e -> e >= rule.bigBlind).collect(Collectors.toList());
        } else if (rule.getCommit(player) == rule.bigBlind && rule.getCommit(nextPlayer) == rule.bigBlind) {
            possibleAmounts =
                    possibleAmounts.stream().filter(e -> e >= rule.bigBlind).collect(Collectors.toList());
        } else {
            float gap = rule.getCommit(player) - rule.getCommit(nextPlayer);
            assert gap > 0;
            possibleAmounts = possibleAmounts.stream().filter(e -> e >= gap * 2).collect(Collectors.toList());
        }
        possibleAmounts = possibleAmounts.stream()
                .filter(e -> e > rule.getCommit(nextPlayer) - rule.getCommit(player))
                .collect(Collectors.toList());
        possibleAmounts = possibleAmounts.stream()
                .filter(e -> e <= rule.stack - rule.getCommit(player))
                .collect(Collectors.toList());
        return possibleAmounts;
    }

    private GameTreeBuilder() {}
}

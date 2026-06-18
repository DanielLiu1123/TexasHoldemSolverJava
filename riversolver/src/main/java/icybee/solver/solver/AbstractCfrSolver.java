package icybee.solver.solver;

import icybee.solver.Card;
import icybee.solver.Deck;
import icybee.solver.RiverRangeManager;
import icybee.solver.compairer.Compairer;
import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.ChanceNode;
import icybee.solver.nodes.GameActions;
import icybee.solver.nodes.GameTreeNode;
import icybee.solver.nodes.ShowdownNode;
import icybee.solver.nodes.TerminalNode;
import icybee.solver.ranges.PrivateCards;
import icybee.solver.ranges.PrivateCardsManager;
import icybee.solver.ranges.RiverCombs;
import icybee.solver.trainable.Trainable;
import icybee.solver.trainable.TrainableFactory;
import icybee.solver.utils.SimdOps;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/**
 * Common state and the full CFR game-tree traversal shared by all CFR-family solvers.
 *
 * <p>The recursion over {@link ActionNode}/{@link ChanceNode}/{@link ShowdownNode}/{@link
 * TerminalNode} — the showdown win/lose sweep, the terminal card-removal payoff, the chance-node
 * reach-probability split, and the action-node regret accumulation — lives here exactly once.
 * Subclasses supply only <em>how</em> a node's children are evaluated via {@link
 * #evaluateChildren}: the single-threaded solver walks them in order, the parallel solver schedules
 * them on a {@link java.util.concurrent.ForkJoinPool}.
 */
abstract class AbstractCfrSolver extends Solver {

    PrivateCards[][] ranges;
    PrivateCards[] range1;
    PrivateCards[] range2;
    int[] initial_board;
    long initial_board_long;
    Compairer compairer;

    Deck deck;
    RiverRangeManager rrm;
    final int player_number = 2;
    int iteration_number;
    PrivateCardsManager pcm;
    boolean debug;
    int print_interval;

    @Nullable
    String logfile;

    TrainableFactory trainerFactory;
    int[] round_deal;
    MonteCarloAlg monteCarloAlg;
    double stop_exploitability;
    TrainingProgressListener progressListener;

    protected AbstractCfrSolver(SolverConfig config) {
        super(config.tree());
        this.initial_board = config.initialBoard();
        this.initial_board_long = Card.boardInts2long(config.initialBoard());
        this.logfile = config.logfile();
        this.trainerFactory = config.trainerFactory();

        PrivateCards[] range1 = this.noDuplicateRange(config.range1(), initial_board_long);
        PrivateCards[] range2 = this.noDuplicateRange(config.range2(), initial_board_long);

        this.range1 = range1;
        this.range2 = range2;
        this.ranges = new PrivateCards[this.player_number][];
        this.ranges[0] = range1;
        this.ranges[1] = range2;
        this.compairer = config.compairer();
        this.deck = config.deck();
        this.rrm = new RiverRangeManager(config.compairer());
        this.iteration_number = config.iterationNumber();

        PrivateCards[][] privateCombos = new PrivateCards[this.player_number][];
        privateCombos[0] = range1;
        privateCombos[1] = range2;
        this.pcm = new PrivateCardsManager(privateCombos, this.player_number, Card.boardInts2long(this.initial_board));
        this.debug = config.debug();
        this.print_interval = config.printInterval();
        this.monteCarloAlg = config.monteCarloAlg();
        this.stop_exploitability = config.stopExploitability();
        this.progressListener = config.progressListener();
        this.round_deal = new int[0];
    }

    PrivateCards[] playerHands(int player) {
        if (player == 0) {
            return range1;
        } else if (player == 1) {
            return range2;
        } else {
            throw new RuntimeException("player not found");
        }
    }

    float[][] getReachProbs() {
        float[][] retval = new float[this.player_number][];
        for (int player = 0; player < this.player_number; player++) {
            PrivateCards[] playerCards = this.playerHands(player);
            float[] reachProb = new float[playerCards.length];
            for (int i = 0; i < playerCards.length; i++) {
                reachProb[i] = playerCards[i].weight;
            }
            retval[player] = reachProb;
        }
        return retval;
    }

    public PrivateCards[] noDuplicateRange(PrivateCards[] privateRange, long boardLong) {
        java.util.List<PrivateCards> rangeArray = new java.util.ArrayList<>();
        java.util.Map<Integer, Boolean> rangeKv = new java.util.HashMap<>();
        for (PrivateCards oneRange : privateRange) {
            if (oneRange == null) throw new RuntimeException();
            if (rangeKv.get(oneRange.hashCode()) != null)
                throw new RuntimeException(String.format("duplicated key %s", oneRange.toString()));
            rangeKv.put(oneRange.hashCode(), Boolean.TRUE);
            long handLong = Card.boardInts2long(new int[] {oneRange.card1, oneRange.card2});
            if (!Card.boardsHasIntercept(handLong, boardLong)) {
                rangeArray.add(oneRange);
            }
        }
        PrivateCards[] ret = new PrivateCards[rangeArray.size()];
        rangeArray.toArray(ret);
        return ret;
    }

    void setTrainable(GameTreeNode root) {
        if (root instanceof ActionNode actionNode) {
            int player = actionNode.getPlayer();
            PrivateCards[] playerPrivates = this.ranges[player];
            actionNode.setTrainable(this.trainerFactory.create(actionNode, playerPrivates));
            List<GameTreeNode> children = actionNode.getChildren();
            for (GameTreeNode oneChild : children) setTrainable(oneChild);
        } else if (root instanceof ChanceNode chanceNode) {
            List<GameTreeNode> children = chanceNode.getChildren();
            for (GameTreeNode oneChild : children) setTrainable(oneChild);
        }
    }

    /**
     * Evaluates each non-null {@code children[k]} under {@code childReachProbs[k]} on board {@code
     * childBoards[k]}, returning the per-index utility arrays (null where the child is null). The
     * scheduling discipline — sequential or work-stealing — is the subclass's only degree of
     * freedom over the shared traversal.
     */
    protected abstract float[][] evaluateChildren(
            int player,
            int iter,
            GameTreeNode parent,
            GameTreeNode[] children,
            float[][][] childReachProbs,
            long[] childBoards);

    float[] cfr(int player, GameTreeNode node, float[][] reachProbs, int iter, long currentBoard) {
        return switch (node.getType()) {
            case ACTION -> actionUtility(player, (ActionNode) node, reachProbs, iter, currentBoard);
            case SHOWDOWN -> showdownUtility(player, (ShowdownNode) node, reachProbs, currentBoard);
            case TERMINAL -> terminalUtility(player, (TerminalNode) node, reachProbs, currentBoard);
            case CHANCE -> chanceUtility(player, (ChanceNode) node, reachProbs, iter, currentBoard);
            default -> throw new RuntimeException("node type unknown");
        };
    }

    float[] actionUtility(int player, ActionNode node, float[][] reachProbs, int iter, long currentBoard) {
        int nodePlayer = node.getPlayer();
        PrivateCards[] nodePlayerPrivateCards = this.ranges[nodePlayer];
        Trainable trainable = Objects.requireNonNull(node.getTrainable(), "trainable not set");

        List<GameTreeNode> children = node.getChildren();
        List<GameActions> actions = node.getActions();
        float[] currentStrategy = trainable.getcurrentStrategy();

        if (this.debug) {
            for (float oneStrategy : currentStrategy) {
                if (Float.isNaN(oneStrategy)) {
                    System.out.println(Arrays.toString(currentStrategy));
                    throw new RuntimeException();
                }
            }
            for (int onePlayer = 0; onePlayer < this.player_number; onePlayer++) {
                for (float oneProb : reachProbs[onePlayer]) {
                    if (Float.isNaN(oneProb)) throw new RuntimeException();
                }
            }
        }
        if (currentStrategy.length != actions.size() * nodePlayerPrivateCards.length) {
            node.printHistory();
            throw new RuntimeException(String.format(
                    "length not match %s - %s \n action size %s private_card size %s",
                    currentStrategy.length,
                    actions.size() * nodePlayerPrivateCards.length,
                    actions.size(),
                    nodePlayerPrivateCards.length));
        }

        int actionCount = actions.size();
        GameTreeNode[] childArr = new GameTreeNode[actionCount];
        float[][][] childReach = new float[actionCount][][];
        long[] childBoards = new long[actionCount];
        for (int actionId = 0; actionId < actionCount; actionId++) {
            float[] playerNewReach = new float[reachProbs[nodePlayer].length];
            SimdOps.mul(
                    currentStrategy,
                    actionId * nodePlayerPrivateCards.length,
                    reachProbs[nodePlayer],
                    playerNewReach,
                    playerNewReach.length);
            float[][] newReach = new float[this.player_number][];
            newReach[1 - nodePlayer] = reachProbs[1 - nodePlayer];
            newReach[nodePlayer] = playerNewReach;
            childArr[actionId] = children.get(actionId);
            childReach[actionId] = newReach;
            childBoards[actionId] = currentBoard;
        }

        float[][] allActionUtility = evaluateChildren(player, iter, node, childArr, childReach, childBoards);

        float[] payoffs = new float[this.ranges[player].length];
        for (int actionId = 0; actionId < actionCount; actionId++) {
            float[] actionUtilities = allActionUtility[actionId];
            if (actionUtilities.length != payoffs.length) {
                node.printHistory();
                throw new RuntimeException(String.format(
                        "action and payoff length not match %s - %s", actionUtilities.length, payoffs.length));
            }
            if (player == nodePlayer) {
                SimdOps.fma(
                        currentStrategy,
                        actionId * nodePlayerPrivateCards.length,
                        actionUtilities,
                        payoffs,
                        actionUtilities.length);
            } else {
                SimdOps.add(actionUtilities, payoffs, actionUtilities.length);
            }
        }

        if (player == nodePlayer) {
            float[] regrets = new float[actionCount * nodePlayerPrivateCards.length];
            for (int actionId = 0; actionId < actionCount; actionId++) {
                SimdOps.sub(
                        allActionUtility[actionId],
                        payoffs,
                        regrets,
                        actionId * nodePlayerPrivateCards.length,
                        nodePlayerPrivateCards.length);
            }
            trainable.updateRegrets(regrets, iter + 1, reachProbs[player]);
        }

        return payoffs;
    }

    float[] chanceUtility(int player, ChanceNode node, float[][] reachProbs, int iter, long currentBoard) {
        List<Card> cards = this.deck.getCards();
        if (cards.size() != node.getChildren().size()) throw new RuntimeException();

        int possibleDeals = node.getChildren().size() - Card.long2board(currentBoard).length - 2;
        List<GameTreeNode> children = node.getChildren();
        List<Card> nodeCards = node.getCards();
        int cardSlots = nodeCards.size();

        if (this.monteCarloAlg == MonteCarloAlg.PUBLIC) {
            int roundIdx = GameTreeNode.gameRound2int(node.getRound());
            int randomDeal;
            if (this.round_deal[roundIdx] == -1) {
                randomDeal = ThreadLocalRandom.current().nextInt(1, possibleDeals + 1 + 2);
                this.round_deal[roundIdx] = randomDeal;
            } else {
                randomDeal = this.round_deal[roundIdx];
            }
            int cardcount = 0;
            for (int card = 0; card < cardSlots; card++) {
                Card oneCard = nodeCards.get(card);
                long cardLong = Card.boardCards2long(new Card[] {oneCard});
                if (Card.boardsHasIntercept(cardLong, currentBoard)) continue;
                cardcount += 1;
                if (cardcount == randomDeal) {
                    float[][] newReachProbs = new float[2][];
                    newReachProbs[player] = new float[reachProbs[player].length];
                    newReachProbs[1 - player] = new float[reachProbs[1 - player].length];
                    for (int onePlayer = 0; onePlayer < 2; onePlayer++) {
                        int handLen = this.ranges[onePlayer].length;
                        for (int hand = 0; hand < handLen; hand++) {
                            PrivateCards onePrivate = this.ranges[onePlayer][hand];
                            if (Card.boardsHasIntercept(cardLong, onePrivate.toBoardLong())) continue;
                            newReachProbs[onePlayer][hand] = reachProbs[onePlayer][hand];
                        }
                    }
                    return cfr(player, children.get(card), newReachProbs, iter, currentBoard | cardLong);
                }
            }
            throw new RuntimeException("not possible");
        }

        GameTreeNode[] childArr = new GameTreeNode[cardSlots];
        float[][][] childReach = new float[cardSlots][][];
        long[] childBoards = new long[cardSlots];
        for (int card = 0; card < cardSlots; card++) {
            GameTreeNode oneChild = children.get(card);
            Card oneCard = nodeCards.get(card);
            long cardLong = Card.boardCards2long(new Card[] {oneCard});

            if (Card.boardsHasIntercept(cardLong, currentBoard)) continue;
            if (oneChild == null || oneCard == null) throw new RuntimeException("child is null");

            PrivateCards[] playerPrivateCard = this.ranges[player];
            PrivateCards[] oppoPrivateCards = this.ranges[1 - player];
            if (playerPrivateCard.length != reachProbs[player].length) throw new RuntimeException("length not match");
            if (oppoPrivateCards.length != reachProbs[1 - player].length)
                throw new RuntimeException("length not match");

            float[][] newReachProbs = new float[2][];
            newReachProbs[player] = new float[playerPrivateCard.length];
            newReachProbs[1 - player] = new float[oppoPrivateCards.length];
            for (int onePlayer = 0; onePlayer < 2; onePlayer++) {
                int handLen = this.ranges[onePlayer].length;
                for (int hand = 0; hand < handLen; hand++) {
                    PrivateCards onePrivate = this.ranges[onePlayer][hand];
                    if (Card.boardsHasIntercept(cardLong, onePrivate.toBoardLong())) continue;
                    newReachProbs[onePlayer][hand] = reachProbs[onePlayer][hand] / possibleDeals;
                }
            }
            childArr[card] = oneChild;
            childReach[card] = newReachProbs;
            childBoards[card] = currentBoard | cardLong;
        }

        float[][] childUtilities = evaluateChildren(player, iter, node, childArr, childReach, childBoards);

        float[] chanceUtility = new float[reachProbs[player].length];
        for (int card = 0; card < cardSlots; card++) {
            float[] childUtility = childUtilities[card];
            if (childUtility == null) continue;
            if (childUtility.length != chanceUtility.length) throw new RuntimeException("length not match");
            for (int i = 0; i < childUtility.length; i++) chanceUtility[i] += childUtility[i];
        }
        return chanceUtility;
    }

    float[] showdownUtility(int player, ShowdownNode node, float[][] reachProbs, long currentBoard) {
        int oppo = 1 - player;
        float winPayoff = (float) node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, player)[player];
        float losePayoff = (float) node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, oppo)[player];
        PrivateCards[] playerPrivateCards = this.ranges[player];
        PrivateCards[] oppoPrivateCards = this.ranges[oppo];

        RiverCombs[] playerCombs = this.rrm.getRiverCombos(player, playerPrivateCards, currentBoard);
        RiverCombs[] oppoCombs = this.rrm.getRiverCombos(oppo, oppoPrivateCards, currentBoard);

        if (this.debug) {
            System.out.println("[PRESHOWDOWN]=======================");
            System.out.println(String.format("player0 reach_prob %s", Arrays.toString(reachProbs[0])));
            System.out.println(String.format("player1 reach_prob %s", Arrays.toString(reachProbs[1])));
            System.out.print("preflop combos: ");
            for (RiverCombs oneRiverComb : playerCombs) {
                System.out.print(String.format("%s(%s) ", oneRiverComb.private_cards.toString(), oneRiverComb.rank));
            }
            System.out.println();
        }

        float[] payoffs = ShowdownPayoffs.compute(
                playerCombs, oppoCombs, reachProbs[oppo], winPayoff, losePayoff, playerPrivateCards.length);

        if (this.debug) {
            System.out.println("[SHOWDOWN]============");
            node.printHistory();
            System.out.println(String.format("loss payoffs: %s", losePayoff));
        }
        return payoffs;
    }

    float[] terminalUtility(int player, TerminalNode node, float[][] reachProb, long currentBoard) {
        double playerPayoff = node.get_payoffs()[player];

        int oppo = 1 - player;
        PrivateCards[] playerHand = playerHands(player);
        PrivateCards[] oppoHand = playerHands(oppo);

        float[] payoffs = new float[this.playerHands(player).length];

        float oppoSum = 0;
        float[] oppoCardSum = new float[52];

        for (int i = 0; i < oppoHand.length; i++) {
            oppoCardSum[oppoHand[i].card1] += reachProb[oppo][i];
            oppoCardSum[oppoHand[i].card2] += reachProb[oppo][i];
            oppoSum += reachProb[oppo][i];
        }

        for (int i = 0; i < playerHand.length; i++) {
            PrivateCards onePlayerHand = playerHand[i];
            if (Card.boardsHasIntercept(
                    currentBoard, Card.boardInts2long(new int[] {onePlayerHand.card1, onePlayerHand.card2}))) {
                continue;
            }
            Integer oppoSameCardInd = this.pcm.indPlayer2Player(player, oppo, i);
            float plusReachProb = oppoSameCardInd == null ? 0 : reachProb[oppo][oppoSameCardInd];
            payoffs[i] = (float) playerPayoff
                    * (oppoSum - oppoCardSum[onePlayerHand.card1] - oppoCardSum[onePlayerHand.card2] + plusReachProb);
        }

        if (this.debug) {
            System.out.println("[TERMINAL]============");
            node.printHistory();
            System.out.println(String.format("PPPayoffs: %s", playerPayoff));
        }
        return payoffs;
    }
}

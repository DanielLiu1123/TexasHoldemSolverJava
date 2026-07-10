package pokersolver.solver;

import static pokersolver.utils.JsonUtil.MAPPER;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pokersolver.Card;
import pokersolver.Deck;
import pokersolver.RiverRangeManager;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameRound;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import pokersolver.ranges.PrivateCards;
import pokersolver.ranges.PrivateCardsManager;
import pokersolver.trainable.Trainable;
import pokersolver.trainable.TrainableFactory;
import pokersolver.utils.SimdOps;
import tools.jackson.databind.node.ObjectNode;

/**
 * Common state and the full CFR game-tree traversal shared by all CFR-family solvers.
 *
 * <p>The recursion — the showdown win/lose sweep, the terminal card-removal payoff, the chance-node
 * reach-probability split, and the action-node regret accumulation — lives here exactly once.
 * Subclasses supply only <em>how</em> a node's children are evaluated via {@link #evaluateChildren}:
 * the single-threaded solver walks them in order, the parallel solver schedules them on a {@link
 * java.util.concurrent.ForkJoinPool}.
 *
 * <p>Everything reachable from the traversal is either immutable or owned by exactly one node, which
 * is what lets the parallel solver fork children without locking. The one exception is each action
 * node's {@link Trainable}, written only by the worker that reached that node with the acting player
 * as its target — and each node is reached once per player per iteration.
 */
abstract class AbstractCfrSolver extends Solver {

    private static final Logger log = LoggerFactory.getLogger(AbstractCfrSolver.class);

    static final int PLAYER_COUNT = 2;

    /** Passed down the recursion when every chance node deals every card. */
    static final double[] NO_SAMPLING = new double[0];

    final PrivateCards[][] ranges;
    final int[] initialBoard;
    final long initialBoardLong;
    final Deck deck;
    final RiverRangeManager riverRanges;
    final PrivateCardsManager privateCards;
    final int iterationNumber;
    final int printInterval;
    final MonteCarloAlg monteCarloAlg;
    final double stopExploitability;
    final TrainingProgressListener progressListener;
    final TrainableFactory trainerFactory;

    @Nullable
    final String logfile;

    /**
     * {@code [player][card] -> indices of that player's hands holding the card}. The chance node
     * zeroes exactly these entries when the card is dealt, instead of testing every hand's mask
     * against it — a hand holds two of 52 cards, so this touches ~4% of the range per card.
     */
    private final int[][][] handsHoldingCard;

    protected AbstractCfrSolver(SolverConfig config) {
        super(config.tree());
        this.initialBoard = config.initialBoard();
        this.initialBoardLong = Card.boardInts2long(config.initialBoard());
        this.logfile = config.logfile();
        this.trainerFactory = config.trainerFactory();
        this.deck = config.deck();

        this.ranges = new PrivateCards[PLAYER_COUNT][];
        this.ranges[0] = playableHands(config.range1(), initialBoardLong);
        this.ranges[1] = playableHands(config.range2(), initialBoardLong);

        this.riverRanges = new RiverRangeManager(config.deck().evaluator());
        this.iterationNumber = config.iterationNumber();
        this.privateCards = new PrivateCardsManager(ranges, PLAYER_COUNT, initialBoardLong);
        this.printInterval = config.printInterval();
        this.monteCarloAlg = config.monteCarloAlg();
        this.stopExploitability = config.stopExploitability();
        this.progressListener = config.progressListener();
        this.handsHoldingCard = indexHandsByCard(ranges);
    }

    /** The hands of {@code range} the initial board does not block. Duplicates are a caller bug. */
    private static PrivateCards[] playableHands(PrivateCards[] range, long boardLong) {
        Set<PrivateCards> seen = new LinkedHashSet<>(range.length * 2);
        List<PrivateCards> playable = new ArrayList<>(range.length);
        for (PrivateCards hand : range) {
            if (!seen.add(Objects.requireNonNull(hand, "null hand in range"))) {
                throw new IllegalArgumentException("duplicate hand in range: " + hand);
            }
            if (!Card.boardsHasIntercept(hand.mask(), boardLong)) playable.add(hand);
        }
        return playable.toArray(new PrivateCards[0]);
    }

    private static int[][][] indexHandsByCard(PrivateCards[][] ranges) {
        int[][][] index = new int[ranges.length][52][];
        for (int player = 0; player < ranges.length; player++) {
            List<List<Integer>> byCard = new ArrayList<>(52);
            for (int card = 0; card < 52; card++) byCard.add(new ArrayList<>());
            PrivateCards[] range = ranges[player];
            for (int hand = 0; hand < range.length; hand++) {
                byCard.get(range[hand].card1).add(hand);
                byCard.get(range[hand].card2).add(hand);
            }
            for (int card = 0; card < 52; card++) {
                index[player][card] =
                        byCard.get(card).stream().mapToInt(Integer::intValue).toArray();
            }
        }
        return index;
    }

    final PrivateCards[] playerHands(int player) {
        return ranges[player];
    }

    final float[][] getReachProbs() {
        float[][] reachProbs = new float[PLAYER_COUNT][];
        for (int player = 0; player < PLAYER_COUNT; player++) {
            PrivateCards[] hands = ranges[player];
            float[] reach = new float[hands.length];
            for (int i = 0; i < hands.length; i++) reach[i] = hands[i].weight;
            reachProbs[player] = reach;
        }
        return reachProbs;
    }

    final void setTrainable(GameTreeNode node) {
        switch (node) {
            case ActionNode action -> {
                action.setTrainable(trainerFactory.create(action, ranges[action.getPlayer()]));
                for (GameTreeNode child : action.getChildren()) setTrainable(child);
            }
            case ChanceNode chance -> {
                for (GameTreeNode child : chance.getChildren()) setTrainable(child);
            }
            case ShowdownNode ignored -> {}
            case TerminalNode ignored -> {}
        }
    }

    /**
     * Draws one card per betting round for this iteration, as fractions in {@code [0, 1)}.
     *
     * <p>Under {@link MonteCarloAlg#PUBLIC} a chance node deals a single sampled card rather than
     * all of them, and every chance node in the same round must deal the same one. Sampling up front
     * and passing the draw down the recursion keeps that agreement without a field the forked
     * workers would race on.
     */
    final double[] sampleRoundDeals() {
        if (monteCarloAlg != MonteCarloAlg.PUBLIC) return NO_SAMPLING;
        double[] samples = new double[GameRound.values().length];
        for (int i = 0; i < samples.length; i++)
            samples[i] = ThreadLocalRandom.current().nextDouble();
        return samples;
    }

    /**
     * Evaluates each non-null {@code children[k]} under {@code childReachProbs[k]} on board {@code
     * childBoards[k]}, returning the per-index utility arrays (null where the child is null). The
     * scheduling discipline — sequential or work-stealing — is the subclass's only degree of freedom
     * over the shared traversal.
     */
    protected abstract float[][] evaluateChildren(
            int player,
            int iteration,
            GameTreeNode parent,
            GameTreeNode[] children,
            float[][][] childReachProbs,
            long[] childBoards,
            double[] roundDeals);

    /** Runs one player's pass over the whole tree, however the subclass schedules it. */
    protected abstract float[] traverse(
            int player, GameTreeNode root, float[][] reachProbs, int iteration, long board, double[] roundDeals);

    /** Called once when training finishes, whether by convergence, exhaustion, or cancellation. */
    protected void onTrainingFinished() {}

    /**
     * Alternating-update CFR: each iteration walks the tree once per player, updating only that
     * player's regrets, then measures exploitability every {@code printInterval} iterations and
     * stops early once it falls below {@code stopExploitability}.
     */
    @Override
    public final void train() throws IOException {
        setTrainable(getTree().getRoot());
        GameTreeNode root = getTree().getRoot();
        BestResponse bestResponse = new BestResponse(ranges, PLAYER_COUNT, privateCards, riverRanges);
        float[][] reachProbs = getReachProbs();
        float pot = (float) root.getPot();

        log.info("iteration 0: exploitability {}% of pot", format(exploitability(bestResponse, root, pot)));

        try (Writer trace = logfile != null
                ? Files.newBufferedWriter(Path.of(logfile), StandardCharsets.UTF_8)
                : Writer.nullWriter()) {
            long since = System.nanoTime();
            for (int iteration = 0; iteration < iterationNumber && !stopRequested; iteration++) {
                for (int player = 0; player < PLAYER_COUNT; player++) {
                    traverse(player, root, reachProbs, iteration, initialBoardLong, sampleRoundDeals());
                }
                if (iteration % printInterval != 0) continue;

                long elapsedMs = (System.nanoTime() - since) / 1_000_000;
                float exploitability = exploitability(bestResponse, root, pot);
                log.info(
                        "iteration {}: exploitability {}% of pot ({} ms)",
                        iteration + 1, format(exploitability), elapsedMs);

                ObjectNode entry = MAPPER.createObjectNode();
                entry.put("iteration", iteration);
                entry.put("exploitability", exploitability);
                entry.put("timeMs", elapsedMs);
                trace.write(entry + "\n");

                progressListener.onProgress(iteration, exploitability, elapsedMs);
                since = System.nanoTime();
                if (stopExploitability > 0 && exploitability < stopExploitability) break;
            }
        } finally {
            onTrainingFinished();
        }
    }

    private float exploitability(BestResponse bestResponse, GameTreeNode root, float pot) {
        return bestResponse.exploitability(root, pot, initialBoardLong);
    }

    private static String format(float exploitability) {
        return String.format("%.6f", exploitability);
    }

    final float[] cfr(int player, GameTreeNode node, float[][] reachProbs, int iteration, long board, double[] deals) {
        return switch (node) {
            case ActionNode action -> actionUtility(player, action, reachProbs, iteration, board, deals);
            case ChanceNode chance -> chanceUtility(player, chance, reachProbs, iteration, board, deals);
            case ShowdownNode showdown -> showdownUtility(player, showdown, reachProbs, board);
            case TerminalNode terminal -> terminalUtility(player, terminal, reachProbs, board);
        };
    }

    private float[] actionUtility(
            int player, ActionNode node, float[][] reachProbs, int iteration, long board, double[] deals) {
        int actor = node.getPlayer();
        int actorHands = ranges[actor].length;
        Trainable trainable = Objects.requireNonNull(node.getTrainable(), "trainable not set");

        List<GameTreeNode> children = node.getChildren();
        List<Action> actions = node.getActions();
        int actionCount = actions.size();
        float[] strategy = trainable.currentStrategy();
        assert strategy.length == actionCount * actorHands
                : "strategy has %d cells, node has %d actions x %d hands"
                        .formatted(strategy.length, actionCount, actorHands);
        assert noNaNs(strategy) : "NaN in strategy at " + node.history();

        // Each action scales the acting player's reach by the probability of taking it; the
        // opponent's reach is unchanged and can be shared rather than copied.
        GameTreeNode[] childArray = new GameTreeNode[actionCount];
        float[][][] childReach = new float[actionCount][][];
        long[] childBoards = new long[actionCount];
        for (int action = 0; action < actionCount; action++) {
            float[] actorReach = new float[actorHands];
            SimdOps.mul(strategy, action * actorHands, reachProbs[actor], actorReach, actorHands);
            float[][] reach = new float[PLAYER_COUNT][];
            reach[actor] = actorReach;
            reach[1 - actor] = reachProbs[1 - actor];
            childArray[action] = children.get(action);
            childReach[action] = reach;
            childBoards[action] = board;
        }

        float[][] actionUtilities =
                evaluateChildren(player, iteration, node, childArray, childReach, childBoards, deals);

        // The node's utility is the strategy-weighted mean over actions for the acting player, and
        // the plain sum for the opponent — whose reach the recursion already folded in.
        float[] payoffs = new float[ranges[player].length];
        for (int action = 0; action < actionCount; action++) {
            float[] utility = actionUtilities[action];
            if (player == actor) {
                SimdOps.fma(strategy, action * actorHands, utility, payoffs, utility.length);
            } else {
                SimdOps.add(utility, payoffs, utility.length);
            }
        }

        if (player == actor) {
            float[] regrets = new float[actionCount * actorHands];
            for (int action = 0; action < actionCount; action++) {
                SimdOps.sub(actionUtilities[action], payoffs, regrets, action * actorHands, actorHands);
            }
            trainable.update(regrets, iteration + 1, reachProbs[player]);
        }
        return payoffs;
    }

    private float[] chanceUtility(
            int player, ChanceNode node, float[][] reachProbs, int iteration, long board, double[] deals) {
        List<GameTreeNode> children = node.getChildren();
        List<Card> cards = node.getCards();
        int cardSlots = cards.size();
        int availableCards = cardSlots - Card.cardCount(board);
        // Two of the remaining cards are in the opponent's hand, so they cannot be dealt.
        int possibleDeals = availableCards - 2;

        if (monteCarloAlg == MonteCarloAlg.PUBLIC) {
            return sampledChanceUtility(player, node, reachProbs, iteration, board, deals, availableCards);
        }

        GameTreeNode[] childArray = new GameTreeNode[cardSlots];
        float[][][] childReach = new float[cardSlots][][];
        long[] childBoards = new long[cardSlots];
        float share = 1f / possibleDeals;
        for (int slot = 0; slot < cardSlots; slot++) {
            Card card = cards.get(slot);
            if (Card.boardsHasIntercept(card.mask(), board)) continue;
            childArray[slot] = children.get(slot);
            childReach[slot] = splitReach(reachProbs, card.getCardInt(), share);
            childBoards[slot] = board | card.mask();
        }

        float[][] childUtilities =
                evaluateChildren(player, iteration, node, childArray, childReach, childBoards, deals);

        float[] utility = new float[reachProbs[player].length];
        for (int slot = 0; slot < cardSlots; slot++) {
            if (childUtilities[slot] != null) SimdOps.add(childUtilities[slot], utility, utility.length);
        }
        return utility;
    }

    /** Deals the one card this round sampled, and recurses into it alone. */
    private float[] sampledChanceUtility(
            int player,
            ChanceNode node,
            float[][] reachProbs,
            int iteration,
            long board,
            double[] deals,
            int availableCards) {
        List<Card> cards = node.getCards();
        int target = 1 + (int) (deals[node.getRound().number() - 1] * availableCards);
        int seen = 0;
        for (int slot = 0; slot < cards.size(); slot++) {
            Card card = cards.get(slot);
            if (Card.boardsHasIntercept(card.mask(), board)) continue;
            if (++seen == target) {
                float[][] reach = splitReach(reachProbs, card.getCardInt(), 1f);
                return cfr(player, node.getChildren().get(slot), reach, iteration, board | card.mask(), deals);
            }
        }
        throw new IllegalStateException(
                "sampled deal %d of %d available cards was not reached".formatted(target, availableCards));
    }

    /**
     * Both players' reach after {@code card} is dealt: scaled by {@code share}, and zeroed for the
     * hands that hold the card and so can no longer be held.
     */
    private float[][] splitReach(float[][] reachProbs, int card, float share) {
        float[][] split = new float[PLAYER_COUNT][];
        for (int player = 0; player < PLAYER_COUNT; player++) {
            float[] reach = reachProbs[player];
            float[] scaled = new float[reach.length];
            SimdOps.scale(reach, share, scaled, reach.length);
            for (int hand : handsHoldingCard[player][card]) scaled[hand] = 0;
            split[player] = scaled;
        }
        return split;
    }

    final float[] showdownUtility(int player, ShowdownNode node, float[][] reachProbs, long board) {
        int opponent = 1 - player;
        float winPayoff = (float) node.payoffsIfWins(player)[player];
        float losePayoff = (float) node.payoffsIfWins(opponent)[player];

        return ShowdownPayoffs.compute(
                riverRanges.getRiverRange(player, ranges[player], board),
                riverRanges.getRiverRange(opponent, ranges[opponent], board),
                reachProbs[opponent],
                winPayoff,
                losePayoff,
                ranges[player].length);
    }

    final float[] terminalUtility(int player, TerminalNode node, float[][] reachProbs, long board) {
        int opponent = 1 - player;
        float payoff = (float) node.getPayoffs()[player];
        PrivateCards[] playerHands = ranges[player];
        PrivateCards[] opponentHands = ranges[opponent];
        float[] opponentReach = reachProbs[opponent];

        // Card removal, in linear time: the opponent's total reach, minus the reach of every combo
        // sharing a card with the player's hand. The two cards' totals double-count the one combo
        // that is exactly the player's hand, so it goes back in.
        float opponentTotal = 0;
        float[] reachHoldingCard = new float[52];
        for (int i = 0; i < opponentHands.length; i++) {
            reachHoldingCard[opponentHands[i].card1] += opponentReach[i];
            reachHoldingCard[opponentHands[i].card2] += opponentReach[i];
            opponentTotal += opponentReach[i];
        }

        float[] payoffs = new float[playerHands.length];
        for (int i = 0; i < playerHands.length; i++) {
            PrivateCards hand = playerHands[i];
            if (Card.boardsHasIntercept(board, hand.mask())) continue;
            int mirror = privateCards.indexInOtherRange(player, opponent, i);
            float sameHandReach = mirror == PrivateCardsManager.NOT_IN_RANGE ? 0 : opponentReach[mirror];
            payoffs[i] = payoff
                    * (opponentTotal - reachHoldingCard[hand.card1] - reachHoldingCard[hand.card2] + sameHandReach);
        }
        return payoffs;
    }

    private static boolean noNaNs(float[] values) {
        for (float value : values) {
            if (Float.isNaN(value)) return false;
        }
        return true;
    }
}

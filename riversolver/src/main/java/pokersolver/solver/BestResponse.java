package pokersolver.solver;

import java.util.List;
import java.util.Objects;
import pokersolver.Card;
import pokersolver.RiverRangeManager;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import pokersolver.ranges.PrivateCards;
import pokersolver.ranges.PrivateCardsManager;
import pokersolver.ranges.RiverRange;
import pokersolver.utils.SimdOps;

/**
 * Measures how far a strategy is from equilibrium.
 *
 * <p>Against a fixed opponent strategy, a player's best response is the pure strategy that maximizes
 * its expected value — at every node the player owns, take the single best action rather than mixing.
 * The value it extracts beyond the game's value is what the player could win by exploiting the
 * current strategy; summed over both players and expressed as a percentage of the pot, that is
 * <em>exploitability</em>, and it reaches zero exactly at a Nash equilibrium.
 *
 * <p>The traversal mirrors {@link AbstractCfrSolver}'s, with one difference at action nodes: where
 * CFR mixes the children by the current strategy, this takes their elementwise maximum.
 */
final class BestResponse {

    private final PrivateCards[][] ranges;
    private final int playerCount;
    private final RiverRangeManager riverRanges;
    private final PrivateCardsManager privateCards;

    BestResponse(
            PrivateCards[][] ranges, int playerCount, PrivateCardsManager privateCards, RiverRangeManager riverRanges) {
        if (ranges.length != playerCount) {
            throw new IllegalArgumentException("%d ranges for %d players".formatted(ranges.length, playerCount));
        }
        if (playerCount != 2) throw new IllegalArgumentException("only two-player games are supported");
        this.ranges = ranges;
        this.playerCount = playerCount;
        this.riverRanges = riverRanges;
        this.privateCards = privateCards;
    }

    /** Both players' exploitability against the tree's current strategy, as a percentage of the pot. */
    float exploitability(GameTreeNode root, float initialPot, long initialBoard) {
        float[][] reachProbs = new float[playerCount][];
        for (int player = 0; player < playerCount; player++) {
            float[] reach = new float[ranges[player].length];
            for (int hand = 0; hand < reach.length; hand++) reach[hand] = ranges[player][hand].weight;
            reachProbs[player] = reach;
        }

        float total = 0;
        for (int player = 0; player < playerCount; player++) {
            total += bestResponseEv(root, player, reachProbs, initialBoard);
        }
        return total / playerCount / initialPot * 100;
    }

    /** {@code player}'s expected value from best-responding, weighted by each hand's relative probability. */
    private float bestResponseEv(GameTreeNode root, int player, float[][] reachProbs, long initialBoard) {
        float[] evPerHand = bestResponse(root, player, reachProbs, initialBoard);
        PrivateCards[] playerRange = ranges[player];
        PrivateCards[] opponentRange = ranges[1 - player];

        float ev = 0;
        for (int hand = 0; hand < playerRange.length; hand++) {
            PrivateCards playerHand = playerRange[hand];
            if (Card.boardsHasIntercept(playerHand.mask(), initialBoard)) continue;

            // The hand's EV is conditional on the opponent holding *something*; normalize by the
            // opponent mass this hand leaves available before weighting by the hand's own share.
            float availableOpponentMass = 0;
            for (PrivateCards opponentHand : opponentRange) {
                if (Card.boardsHasIntercept(opponentHand.mask(), initialBoard | playerHand.mask())) continue;
                availableOpponentMass += opponentHand.weight;
            }
            ev += evPerHand[hand] * playerHand.relativeProb() / availableOpponentMass;
        }
        return ev;
    }

    private float[] bestResponse(GameTreeNode node, int player, float[][] reachProbs, long board) {
        return switch (node) {
            case ActionNode action -> actionBestResponse(action, player, reachProbs, board);
            case ChanceNode chance -> chanceBestResponse(chance, player, reachProbs, board);
            case ShowdownNode showdown -> showdownBestResponse(showdown, player, reachProbs, board);
            case TerminalNode terminal -> terminalBestResponse(terminal, player, reachProbs, board);
        };
    }

    private float[] actionBestResponse(ActionNode node, int player, float[][] reachProbs, long board) {
        List<GameTreeNode> children = node.getChildren();

        if (player == node.getPlayer()) {
            // Best-responding: take the best action per hand, so the children combine by max.
            float[] best = null;
            for (GameTreeNode child : children) {
                float[] childEv = bestResponse(child, player, reachProbs, board);
                if (best == null) {
                    best = childEv;
                } else {
                    for (int hand = 0; hand < best.length; hand++) best[hand] = Math.max(best[hand], childEv[hand]);
                }
            }
            return Objects.requireNonNull(best, "action node has no children");
        }

        // The opponent plays its average strategy: fold its action probabilities into its reach and
        // sum the children, which is what the recursion already weights by.
        int opponent = node.getPlayer();
        int opponentHands = reachProbs[opponent].length;
        float[] strategy =
                Objects.requireNonNull(node.getTrainable(), "trainable not set").averageStrategy();
        if (strategy.length != children.size() * opponentHands) {
            throw new IllegalStateException("strategy has %d cells, node has %d actions x %d hands"
                    .formatted(strategy.length, children.size(), opponentHands));
        }

        float[] total = new float[ranges[player].length];
        for (int action = 0; action < children.size(); action++) {
            float[] opponentReach = new float[opponentHands];
            SimdOps.mul(strategy, action * opponentHands, reachProbs[opponent], opponentReach, opponentHands);

            float[][] childReach = new float[playerCount][];
            childReach[opponent] = opponentReach;
            childReach[player] = reachProbs[player];

            float[] childEv = bestResponse(children.get(action), player, childReach, board);
            SimdOps.add(childEv, total, total.length);
        }
        return total;
    }

    private float[] chanceBestResponse(ChanceNode node, int player, float[][] reachProbs, long board) {
        List<Card> cards = node.getCards();
        int possibleDeals = cards.size() - Card.cardCount(board) - 2;
        float share = 1f / possibleDeals;

        float[] total = new float[reachProbs[player].length];
        for (int slot = 0; slot < cards.size(); slot++) {
            Card card = cards.get(slot);
            if (Card.boardsHasIntercept(card.mask(), board)) continue;

            float[][] childReach = new float[playerCount][];
            for (int p = 0; p < playerCount; p++) {
                float[] reach = new float[ranges[p].length];
                for (int hand = 0; hand < reach.length; hand++) {
                    if (Card.boardsHasIntercept(card.mask(), ranges[p][hand].mask())) continue;
                    reach[hand] = reachProbs[p][hand] * share;
                }
                childReach[p] = reach;
            }

            float[] childEv = bestResponse(node.getChildren().get(slot), player, childReach, board | card.mask());
            SimdOps.add(childEv, total, total.length);
        }
        return total;
    }

    private float[] terminalBestResponse(TerminalNode node, int player, float[][] reachProbs, long board) {
        int opponent = 1 - player;
        float payoff = (float) node.getPayoffs()[player];
        RiverRange playerRange = riverRanges.getRiverRange(player, ranges[player], board);
        RiverRange opponentRange = riverRanges.getRiverRange(opponent, ranges[opponent], board);
        float[] opponentReach = reachProbs[opponent];

        float opponentTotal = 0;
        float[] reachHoldingCard = new float[52];
        for (int i = 0; i < opponentRange.size(); i++) {
            float reach = opponentReach[opponentRange.reachIndex()[i]];
            opponentTotal += reach;
            reachHoldingCard[opponentRange.card1()[i]] += reach;
            reachHoldingCard[opponentRange.card2()[i]] += reach;
        }

        float[] payoffs = new float[ranges[player].length];
        for (int i = 0; i < playerRange.size(); i++) {
            int hand = playerRange.reachIndex()[i];
            int mirror = privateCards.indexInOtherRange(player, opponent, hand);
            float sameHandReach = mirror == PrivateCardsManager.NOT_IN_RANGE ? 0 : opponentReach[mirror];
            payoffs[hand] = payoff
                    * (opponentTotal
                            - reachHoldingCard[playerRange.card1()[i]]
                            - reachHoldingCard[playerRange.card2()[i]]
                            + sameHandReach);
        }
        return payoffs;
    }

    private float[] showdownBestResponse(ShowdownNode node, int player, float[][] reachProbs, long board) {
        int opponent = 1 - player;
        return ShowdownPayoffs.compute(
                riverRanges.getRiverRange(player, ranges[player], board),
                riverRanges.getRiverRange(opponent, ranges[opponent], board),
                reachProbs[opponent],
                (float) node.payoffsIfWins(player)[player],
                (float) node.payoffsIfWins(opponent)[player],
                ranges[player].length);
    }
}

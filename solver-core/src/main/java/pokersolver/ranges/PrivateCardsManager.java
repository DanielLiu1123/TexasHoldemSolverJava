package pokersolver.ranges;

import java.util.Arrays;
import pokersolver.Card;

/**
 * Cross-references the two players' ranges on a fixed initial board.
 *
 * <p>Two things the CFR traversal needs constantly and neither range knows on its own: where a hand
 * held by one player sits in the <em>other</em> player's range ({@link #indexInOtherRange}, for the
 * card-removal correction at fold nodes), and each hand's relative probability once the opponent's
 * range and the board have blocked what they block ({@link PrivateCards#relativeProb()}, which
 * weights the best-response EV).
 */
public final class PrivateCardsManager {

    /** Absent from the other player's range. */
    public static final int NOT_IN_RANGE = -1;

    private static final int HAND_KEYS = 52 * 52;

    private final PrivateCards[][] privateCards;
    private final int playerNumber;
    private final long initialBoard;

    /** {@code [hand key][player] -> index in that player's range}, or {@link #NOT_IN_RANGE}. */
    private final int[] rangeIndexByHand;

    public PrivateCardsManager(PrivateCards[][] privateCards, int playerNumber, long initialBoard) {
        this.privateCards = privateCards;
        this.playerNumber = playerNumber;
        this.initialBoard = initialBoard;

        this.rangeIndexByHand = new int[HAND_KEYS * playerNumber];
        Arrays.fill(rangeIndexByHand, NOT_IN_RANGE);
        for (int player = 0; player < playerNumber; player++) {
            PrivateCards[] range = privateCards[player];
            for (int i = 0; i < range.length; i++) {
                rangeIndexByHand[range[i].hashCode() * playerNumber + player] = i;
            }
        }
        setRelativeProbs();
    }

    public PrivateCards[] getPreflopCards(int player) {
        return privateCards[player];
    }

    /**
     * Where {@code toPlayer}'s range holds the same two cards that sit at {@code index} in {@code
     * fromPlayer}'s range, or {@link #NOT_IN_RANGE} if it does not hold them at all.
     */
    public int indexInOtherRange(int fromPlayer, int toPlayer, int index) {
        PrivateCards[] from = privateCards[fromPlayer];
        if (index < 0 || index >= from.length) {
            throw new IndexOutOfBoundsException("hand index %d outside player %d's range".formatted(index, fromPlayer));
        }
        return rangeIndexByHand[from[index].hashCode() * playerNumber + toPlayer];
    }

    public float[] getInitialReachProb(int player, long board) {
        PrivateCards[] range = privateCards[player];
        float[] probs = new float[range.length];
        for (int i = 0; i < range.length; i++) {
            probs[i] = Card.boardsHasIntercept(board, range[i].mask()) ? 0 : range[i].weight;
        }
        return probs;
    }

    /**
     * Weights every hand by how much of the opponent's range it leaves unblocked, then normalizes
     * across the player's range so the weights sum to one.
     */
    private void setRelativeProbs() {
        for (int player = 0; player < privateCards.length; player++) {
            PrivateCards[] range = privateCards[player];
            PrivateCards[] opponent = privateCards[1 - player];
            float total = 0;

            for (PrivateCards hand : range) {
                if (Card.boardsHasIntercept(hand.mask(), initialBoard)) continue;
                float unblockedOpponentMass = 0;
                for (PrivateCards oppoHand : opponent) {
                    if (Card.boardsHasIntercept(oppoHand.mask(), initialBoard | hand.mask())) continue;
                    unblockedOpponentMass += oppoHand.weight;
                }
                hand.relativeProb = unblockedOpponentMass * hand.weight;
                total += hand.relativeProb;
            }
            for (PrivateCards hand : range) hand.relativeProb /= total;
        }
    }
}

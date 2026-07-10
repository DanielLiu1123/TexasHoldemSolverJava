package pokersolver.ranges;

import pokersolver.Card;

/**
 * One two-card holding in a player's range, with the weight the range assigns it.
 *
 * <p>The pair's board mask is precomputed: the CFR traversal tests it against the running board once
 * per hand per node, and rebuilding it from the two card ids each time showed up in profiles.
 */
public final class PrivateCards {

    public final int card1;
    public final int card2;
    public final float weight;

    /** {@code (1L << card1) | (1L << card2)}. */
    private final long mask;

    /**
     * This hand's share of the combos the opponent's range leaves available, normalized across the
     * player's range. Assigned once by {@link PrivateCardsManager} at construction.
     */
    float relativeProb;

    public PrivateCards(int card1, int card2, float weight) {
        this.card1 = card1;
        this.card2 = card2;
        this.weight = weight;
        this.mask = (1L << card1) | (1L << card2);
    }

    /** This hand as a two-bit board mask. */
    public long mask() {
        return mask;
    }

    public float relativeProb() {
        return relativeProb;
    }

    /** A suit-aware key for the unordered pair, in {@code [0, 52 * 52)}. */
    public static int hashHand(int card1, int card2) {
        return card1 > card2 ? card1 * 52 + card2 : card2 * 52 + card1;
    }

    @Override
    public int hashCode() {
        return hashHand(card1, card2);
    }

    @Override
    public boolean equals(Object other) {
        // The pair is unordered: (As, Kd) and (Kd, As) are the same holding.
        return other instanceof PrivateCards that
                && Math.min(card1, card2) == Math.min(that.card1, that.card2)
                && Math.max(card1, card2) == Math.max(that.card1, that.card2);
    }

    @Override
    public String toString() {
        return card1 > card2
                ? Card.intCard2Str(card1) + Card.intCard2Str(card2)
                : Card.intCard2Str(card2) + Card.intCard2Str(card1);
    }

    /** The canonical hand label: {@code "AA"}, {@code "AKs"}, {@code "AKo"}. */
    public String summary() {
        String high = Card.intCard2Str(Math.max(card1, card2));
        String low = Card.intCard2Str(Math.min(card1, card2));
        if (high.charAt(0) == low.charAt(0)) return String.valueOf(high.charAt(0)) + low.charAt(0);
        return String.valueOf(high.charAt(0)) + low.charAt(0) + (high.charAt(1) == low.charAt(1) ? 's' : 'o');
    }

    public int[] getHands() {
        return card1 > card2 ? new int[] {card1, card2} : new int[] {card2, card1};
    }
}

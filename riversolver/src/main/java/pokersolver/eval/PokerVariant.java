package pokersolver.eval;

import java.util.List;
import pokersolver.Deck;

/**
 * A poker variant, defined by the ranks its deck contains and by how it orders the nine {@link
 * HandCategory hand categories}.
 *
 * <p>Short-deck removes the deuces through fives, which makes flushes scarcer than full houses and
 * straights more common than trips — so it reorders both pairs relative to standard hold'em. The
 * wheel straight follows the deck: {@code A-2-3-4-5} in hold'em, {@code A-6-7-8-9} in short-deck.
 */
public enum PokerVariant {

    /** Standard 52-card Texas hold'em: deuce through ace. */
    HOLDEM(
            2,
            List.of(
                    HandCategory.HIGH_CARD,
                    HandCategory.ONE_PAIR,
                    HandCategory.TWO_PAIR,
                    HandCategory.THREE_OF_A_KIND,
                    HandCategory.STRAIGHT,
                    HandCategory.FLUSH,
                    HandCategory.FULL_HOUSE,
                    HandCategory.FOUR_OF_A_KIND,
                    HandCategory.STRAIGHT_FLUSH)),

    /** Six-plus (short-deck) hold'em: six through ace. Flushes beat full houses, trips beat straights. */
    SHORT_DECK(
            6,
            List.of(
                    HandCategory.HIGH_CARD,
                    HandCategory.ONE_PAIR,
                    HandCategory.TWO_PAIR,
                    HandCategory.STRAIGHT,
                    HandCategory.THREE_OF_A_KIND,
                    HandCategory.FULL_HOUSE,
                    HandCategory.FLUSH,
                    HandCategory.FOUR_OF_A_KIND,
                    HandCategory.STRAIGHT_FLUSH));

    private final int lowestRank;
    private final List<HandCategory> weakestFirst;

    PokerVariant(int lowestRank, List<HandCategory> weakestFirst) {
        this.lowestRank = lowestRank;
        this.weakestFirst = weakestFirst;
    }

    /** The lowest card rank in the deck: 2 for hold'em, 6 for short-deck. */
    public int lowestRank() {
        return lowestRank;
    }

    /** The number of distinct ranks in the deck: 13 for hold'em, 9 for short-deck. */
    public int rankCount() {
        return 15 - lowestRank;
    }

    /** How many hand categories this variant ranks below {@code category}. Higher is stronger. */
    int strengthOf(HandCategory category) {
        return weakestFirst.indexOf(category);
    }

    /**
     * The variant whose deck has {@code rankCount} ranks.
     *
     * @throws IllegalArgumentException if no variant has that many ranks
     */
    public static PokerVariant forRankCount(int rankCount) {
        for (PokerVariant variant : values()) {
            if (variant.rankCount() == rankCount) return variant;
        }
        throw new IllegalArgumentException(
                "no poker variant has a %d-rank deck (expected 13 for hold'em or 9 for short-deck)"
                        .formatted(rankCount));
    }

    /** The variant matching {@code deck}'s rank set. */
    public static PokerVariant forDeck(Deck deck) {
        return forRankCount(deck.getRanks().size());
    }
}

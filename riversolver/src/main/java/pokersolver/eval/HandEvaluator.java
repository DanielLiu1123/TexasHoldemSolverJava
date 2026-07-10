package pokersolver.eval;

import pokersolver.Deck;

/**
 * Ranks five- to seven-card poker hands by strength.
 *
 * <p>Cards are 52-bit masks: bit {@code (rank - 2) * 4 + suit}, the encoding {@code Card} produces.
 * A rank is a dense integer in {@code [1, distinctRanks()]} where <b>smaller is stronger</b> — rank
 * 1 is a royal flush — and equal ranks mean the hands tie at showdown. Nothing else about the value
 * is meaningful; only the ordering is.
 *
 * <p>Implementations are immutable and safe for concurrent use.
 */
public interface HandEvaluator {

    /**
     * The rank of the best five-card hand contained in {@code cards}.
     *
     * @param cards a 52-bit card mask with five to seven bits set
     */
    int rank(long cards);

    /** The rank of the best five-card hand across a player's hole cards and the community board. */
    default int rank(long hand, long board) {
        return rank(hand | board);
    }

    /**
     * Compares two hands on a shared board, as {@link Integer#compare} would: positive when {@code
     * handA} is stronger, zero on a tie.
     */
    default int compare(long handA, long handB, long board) {
        return Integer.compare(rank(handB | board), rank(handA | board));
    }

    /** The number of distinct hand strengths: 7462 for hold'em, 1404 for short-deck. */
    int distinctRanks();

    /** The evaluator for {@code variant}. Tables are built once per variant and shared. */
    static HandEvaluator forVariant(PokerVariant variant) {
        return TableHandEvaluator.of(variant);
    }

    /** The evaluator matching {@code deck}'s rank set. */
    static HandEvaluator forDeck(Deck deck) {
        return forVariant(PokerVariant.forDeck(deck));
    }
}

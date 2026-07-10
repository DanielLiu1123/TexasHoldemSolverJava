package pokersolver.eval;

/**
 * Ranks five- to seven-card Texas hold'em hands by strength.
 *
 * <p>Cards are 52-bit masks: bit {@code (rank - 2) * 4 + suit}, the encoding {@link pokersolver.Card}
 * produces. A rank is a dense integer in {@code [1, }{@value RankTables#DISTINCT_RANKS}{@code ]} where
 * <b>smaller is stronger</b> — rank 1 is a royal flush — and equal ranks mean the hands tie at
 * showdown. Nothing else about the value is meaningful; only the ordering is.
 *
 * <p>{@link Long#compress} splits the card mask into one 13-bit rank bitmask per suit — a single
 * {@code PEXT} instruction where the hardware offers one. Five or more bits in any of them means a
 * flush, which {@link RankTables#FLUSH_RANKS} answers directly. Otherwise the four bitmasks are
 * summed rank by rank into the count vector that {@link RankTables#quinaryIndex} hashes, and {@link
 * RankTables#NON_FLUSH_RANKS} answers that. See {@link RankTables} for why those paths are exhaustive
 * and disjoint.
 *
 * <p>The tables are immutable and built once; every method here is a pure function of its arguments
 * and safe to call from any thread.
 */
public final class HandEvaluator {

    /** Bits 0, 4, ..., 48: one per rank, selecting the lowest suit. Shift left by suit for the rest. */
    private static final long SUIT_STRIDE = 0x1111111111111L;

    /** The number of distinct hand strengths: rank 1 (royal flush) through 7462 (7-5-4-3-2). */
    public static final int DISTINCT_RANKS = RankTables.DISTINCT_RANKS;

    private HandEvaluator() {}

    /**
     * The rank of the best five-card hand contained in {@code cards}.
     *
     * @param cards a 52-bit card mask with five to seven bits set
     */
    public static int rank(long cards) {
        int clubs = (int) Long.compress(cards, SUIT_STRIDE);
        int diamonds = (int) Long.compress(cards, SUIT_STRIDE << 1);
        int hearts = (int) Long.compress(cards, SUIT_STRIDE << 2);
        int spades = (int) Long.compress(cards, SUIT_STRIDE << 3);

        if (Integer.bitCount(clubs) >= 5) return RankTables.FLUSH_RANKS[clubs];
        if (Integer.bitCount(diamonds) >= 5) return RankTables.FLUSH_RANKS[diamonds];
        if (Integer.bitCount(hearts) >= 5) return RankTables.FLUSH_RANKS[hearts];
        if (Integer.bitCount(spades) >= 5) return RankTables.FLUSH_RANKS[spades];

        // Inlined RankTables.quinaryIndex: the count vector is read a rank at a time out of the four
        // suit bitmasks rather than materialized into an int[].
        int cardCount = Long.bitCount(cards);
        int index = RankTables.SIZE_OFFSETS[cardCount];
        int remaining = cardCount;
        for (int rank = RankTables.RANK_COUNT - 1; rank >= 0 && remaining > 0; rank--) {
            int count = ((clubs >>> rank) & 1)
                    + ((diamonds >>> rank) & 1)
                    + ((hearts >>> rank) & 1)
                    + ((spades >>> rank) & 1);
            index += RankTables.CUMULATIVE_WAYS[(rank * 8 + remaining) * 5 + count];
            remaining -= count;
        }
        return RankTables.NON_FLUSH_RANKS[index];
    }

    /** The rank of the best five-card hand across a player's hole cards and the community board. */
    public static int rank(long hand, long board) {
        return rank(hand | board);
    }

    /**
     * Compares two hands on a shared board, as {@link Integer#compare} would: positive when {@code
     * handA} is stronger, zero on a tie.
     */
    public static int compare(long handA, long handB, long board) {
        return Integer.compare(rank(handB | board), rank(handA | board));
    }
}

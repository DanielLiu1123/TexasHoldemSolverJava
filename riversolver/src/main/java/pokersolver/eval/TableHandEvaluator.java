package pokersolver.eval;

import java.util.EnumMap;
import java.util.Map;

/**
 * The {@link HandEvaluator} implementation: two table reads and no branching worth the name.
 *
 * <p>{@link Long#compress} splits the 52-bit card mask into one 13-bit rank bitmask per suit — a
 * single {@code PEXT} instruction where the hardware offers one. Five or more bits in any of them
 * means a flush, which {@link RankTables#flushRanks} answers directly. Otherwise the four bitmasks
 * are summed rank by rank into the count vector that {@link RankTables#quinaryIndex} hashes, and
 * {@link RankTables#nonFlushRanks} answers that.
 *
 * <p>See {@link RankTables} for why those two paths are exhaustive and disjoint.
 */
final class TableHandEvaluator implements HandEvaluator {

    /** Bits 0, 4, ..., 48: one per rank, selecting the lowest suit. Shift left by suit for the rest. */
    private static final long SUIT_STRIDE = 0x1111111111111L;

    private static final Map<PokerVariant, TableHandEvaluator> INSTANCES = new EnumMap<>(PokerVariant.class);

    private final RankTables tables;
    private final int rankOffset;

    private TableHandEvaluator(PokerVariant variant) {
        this.tables = new RankTables(variant);
        this.rankOffset = variant.lowestRank() - 2;
    }

    static synchronized TableHandEvaluator of(PokerVariant variant) {
        return INSTANCES.computeIfAbsent(variant, TableHandEvaluator::new);
    }

    @Override
    public int rank(long cards) {
        int clubs = (int) Long.compress(cards, SUIT_STRIDE) >>> rankOffset;
        int diamonds = (int) Long.compress(cards, SUIT_STRIDE << 1) >>> rankOffset;
        int hearts = (int) Long.compress(cards, SUIT_STRIDE << 2) >>> rankOffset;
        int spades = (int) Long.compress(cards, SUIT_STRIDE << 3) >>> rankOffset;

        if (Integer.bitCount(clubs) >= 5) return tables.flushRanks[clubs];
        if (Integer.bitCount(diamonds) >= 5) return tables.flushRanks[diamonds];
        if (Integer.bitCount(hearts) >= 5) return tables.flushRanks[hearts];
        if (Integer.bitCount(spades) >= 5) return tables.flushRanks[spades];

        // Inlined RankTables.quinaryIndex: the count vector is read a rank at a time out of the
        // four suit bitmasks rather than materialized into an int[].
        int cardCount = Long.bitCount(cards);
        int index = tables.sizeOffsets[cardCount];
        int remaining = cardCount;
        for (int rank = tables.rankCount - 1; rank >= 0 && remaining > 0; rank--) {
            int count = ((clubs >>> rank) & 1)
                    + ((diamonds >>> rank) & 1)
                    + ((hearts >>> rank) & 1)
                    + ((spades >>> rank) & 1);
            index += tables.cumulativeWays[(rank * 8 + remaining) * 5 + count];
            remaining -= count;
        }
        return tables.nonFlushRanks[index];
    }

    @Override
    public int distinctRanks() {
        return tables.distinctRanks;
    }
}

package pokersolver.eval;

import java.util.Arrays;

/**
 * The two lookup tables behind {@link TableHandEvaluator}, derived from a {@link PokerVariant}'s
 * rules rather than read from a dictionary file.
 *
 * <p>A hand of five to seven cards resolves down one of two disjoint paths:
 *
 * <ul>
 *   <li><b>Flush.</b> If some suit holds five or more cards, the best five cards are all of that
 *       suit — a straight flush or a flush. (Quads or a full house alongside a five-card suit would
 *       need at least eight cards, so with seven there is nothing stronger to find off-suit.) {@link
 *       #flushRanks} maps that suit's rank bitmask straight to a rank.
 *   <li><b>Everything else.</b> With no five-card suit, no five-card subset is a flush either, so
 *       the hand is decided entirely by how many cards it holds of each rank. {@link #nonFlushRanks}
 *       maps that count vector — via the minimal perfect hash in {@link #cumulativeWays} — to a
 *       rank.
 * </ul>
 *
 * <p>Both tables store the same dense rank: {@code 1} is the strongest hand (a royal flush) and
 * {@link #distinctRanks} the weakest, so a smaller rank always means a stronger hand and equal ranks
 * mean a tie. The numbering reproduces the historical dictionary files exactly, which {@code
 * HandEvaluatorGoldenTest} verifies entry by entry.
 *
 * <p>Together the tables cost about 150 KB for hold'em, against the ~50 MB and 2.6M-entry hash map
 * of the dictionary they replace, and a lookup is a handful of array reads rather than up to 21 hash
 * probes.
 */
final class RankTables {

    /** Ranks are packed five to an int, four bits each, so a strength key fits in 24 bits. */
    private static final int RANK_BITS = 4;

    private static final int MAX_CARDS = 7;

    /** Remaining-card slots addressable by {@link #cumulativeWays}: 0..7. */
    private static final int CARD_SLOTS = MAX_CARDS + 1;

    /** The number of distinct ranks in the deck: 13 (hold'em) or 9 (short-deck). */
    final int rankCount;

    /** The number of distinct hand strengths: 7462 (hold'em), 1404 (short-deck). */
    final int distinctRanks;

    /** Indexed by a suit's rank bitmask (five to seven bits set). */
    final short[] flushRanks;

    /** Indexed by {@link #quinaryIndex}. */
    final short[] nonFlushRanks;

    /**
     * Minimal perfect hash for rank-count vectors, flattened as {@code [(rank * 8 + remaining) * 5 +
     * count]}. Entry {@code (r, k, c)} counts the vectors that place fewer than {@code c} cards on
     * rank {@code r} while filling the remaining {@code k} slots from ranks below it — the standard
     * combinatorial number system, generalized from sets to multisets.
     */
    final int[] cumulativeWays;

    /** Where each hand size's block of {@link #nonFlushRanks} starts, indexed by card count. */
    final int[] sizeOffsets;

    /** How many categories each category outranks, indexed by {@link HandCategory#ordinal()}. */
    @SuppressWarnings("EnumOrdinal") // an ordinal-indexed private array, which is what ordinal() is for
    private final int[] categoryStrength;

    /** Straight rank bitmasks, strongest first; the wheel (ace playing low) comes last. */
    private final int[] straightMasks;

    /** The tiebreaker rank of each entry in {@link #straightMasks} — the wheel's is its five-high end. */
    private final int[] straightHighs;

    @SuppressWarnings("EnumOrdinal") // ordinal-indexed private array
    RankTables(PokerVariant variant) {
        this.rankCount = variant.rankCount();
        this.categoryStrength = new int[HandCategory.values().length];
        for (HandCategory category : HandCategory.values()) {
            categoryStrength[category.ordinal()] = variant.strengthOf(category);
        }

        int straightCount = rankCount - 3; // (rankCount - 4) run straights, plus the wheel
        this.straightMasks = new int[straightCount];
        this.straightHighs = new int[straightCount];
        int size = 0;
        for (int high = rankCount - 1; high >= 4; high--) {
            straightMasks[size] = 0b11111 << (high - 4);
            straightHighs[size++] = high;
        }
        // The wheel: the ace plays below the deck's four lowest ranks, which cap the straight.
        straightMasks[size] = (1 << (rankCount - 1)) | 0b1111;
        straightHighs[size] = 3;

        int[][] waysBelow = waysToFill(rankCount);
        this.cumulativeWays = cumulativeWays(waysBelow);
        this.sizeOffsets = sizeOffsets(waysBelow[rankCount]);

        int[] strengthKeys = allFiveCardStrengthKeys();
        this.distinctRanks = strengthKeys.length;
        this.flushRanks = buildFlushRanks(strengthKeys);
        this.nonFlushRanks = buildNonFlushRanks(strengthKeys);
    }

    /**
     * Index of the rank-count vector {@code counts} (one entry per rank, each 0..4, summing to
     * {@code cardCount}) within the block for hands of that size.
     */
    int quinaryIndex(int[] counts, int cardCount) {
        int index = sizeOffsets[cardCount];
        int remaining = cardCount;
        for (int rank = rankCount - 1; rank >= 0 && remaining > 0; rank--) {
            int count = counts[rank];
            index += cumulativeWays[(rank * CARD_SLOTS + remaining) * 5 + count];
            remaining -= count;
        }
        return index;
    }

    // ---------------------------------------------------------------------------------------
    // Strength keys: a total order over five-card hands, before they collapse to dense ranks.
    // ---------------------------------------------------------------------------------------

    /**
     * A comparable strength key: the category's variant-specific strength, then up to five
     * tiebreaker ranks in the order the category compares them (quad rank before kicker, trips rank
     * before its two kickers, and so on). Larger keys are stronger hands.
     */
    @SuppressWarnings("EnumOrdinal") // ordinal-indexed private array
    private int strengthKey(HandCategory category, int... tiebreakers) {
        int key = categoryStrength[category.ordinal()];
        for (int slot = 0; slot < 5; slot++) {
            int rank = slot < tiebreakers.length ? tiebreakers[slot] + 1 : 0;
            key = (key << RANK_BITS) | rank;
        }
        return key;
    }

    /** Every distinct five-card strength key, strongest first. Its length is {@link #distinctRanks}. */
    private int[] allFiveCardStrengthKeys() {
        int[] keys = new int[binomial(rankCount, 5) + (sizeOffsets[6] - sizeOffsets[5])];
        int size = 0;
        for (int mask = 0; mask < (1 << rankCount); mask++) {
            if (Integer.bitCount(mask) == 5) keys[size++] = flushStrengthKey(mask);
        }
        size = collectNonFlushKeys(new int[rankCount], rankCount - 1, 5, keys, size);

        int[] distinct = Arrays.stream(keys, 0, size).distinct().toArray();
        Arrays.sort(distinct);
        for (int i = 0, j = distinct.length - 1; i < j; i++, j--) {
            int swap = distinct[i];
            distinct[i] = distinct[j];
            distinct[j] = swap;
        }
        return distinct;
    }

    /** Appends the strength key of every rank-count vector summing to five. */
    private int collectNonFlushKeys(int[] counts, int rank, int remaining, int[] keys, int size) {
        if (remaining == 0) {
            keys[size++] = nonFlushStrengthKey(counts);
            return size;
        }
        if (rank < 0) return size;
        for (int count = 0; count <= Math.min(4, remaining); count++) {
            counts[rank] = count;
            size = collectNonFlushKeys(counts, rank - 1, remaining - count, keys, size);
        }
        counts[rank] = 0;
        return size;
    }

    /** The strength key of the best five cards drawn from one suit's rank bitmask. */
    private int flushStrengthKey(int suitMask) {
        for (int i = 0; i < straightMasks.length; i++) {
            if ((suitMask & straightMasks[i]) == straightMasks[i]) {
                return strengthKey(HandCategory.STRAIGHT_FLUSH, straightHighs[i]);
            }
        }
        return strengthKey(HandCategory.FLUSH, topRanks(suitMask));
    }

    /**
     * The strength key of the five cards described by {@code counts}, evaluated as a non-flush hand.
     * The counts must sum to five.
     */
    private int nonFlushStrengthKey(int[] counts) {
        // Tiebreakers come out in the order every category compares them, which is exactly
        // (count descending, rank descending): quad before kicker, trips before pair, and so on.
        int[] ordered = new int[5];
        int size = 0;
        for (int count = 4; count >= 1; count--) {
            for (int rank = rankCount - 1; rank >= 0; rank--) {
                if (counts[rank] == count) ordered[size++] = rank;
            }
        }
        int[] tiebreakers = Arrays.copyOf(ordered, size);
        return switch (size) {
            case 2 ->
                counts[tiebreakers[0]] == 4
                        ? strengthKey(HandCategory.FOUR_OF_A_KIND, tiebreakers)
                        : strengthKey(HandCategory.FULL_HOUSE, tiebreakers);
            case 3 ->
                counts[tiebreakers[0]] == 3
                        ? strengthKey(HandCategory.THREE_OF_A_KIND, tiebreakers)
                        : strengthKey(HandCategory.TWO_PAIR, tiebreakers);
            case 4 -> strengthKey(HandCategory.ONE_PAIR, tiebreakers);
            case 5 -> straightOrHighCardKey(tiebreakers);
            default -> throw new IllegalStateException("not a five-card hand: " + Arrays.toString(counts));
        };
    }

    /** Five distinct ranks, descending: a straight if they run consecutively, otherwise a high card. */
    private int straightOrHighCardKey(int[] descendingRanks) {
        int mask = 0;
        for (int rank : descendingRanks) mask |= 1 << rank;
        for (int i = 0; i < straightMasks.length; i++) {
            if (mask == straightMasks[i]) return strengthKey(HandCategory.STRAIGHT, straightHighs[i]);
        }
        return strengthKey(HandCategory.HIGH_CARD, descendingRanks);
    }

    /** The five highest set bits of {@code mask}, as rank indices, descending. */
    private static int[] topRanks(int mask) {
        int[] ranks = new int[5];
        for (int i = 0; i < 5; i++) {
            int highest = 31 - Integer.numberOfLeadingZeros(mask);
            ranks[i] = highest;
            mask &= ~(1 << highest);
        }
        return ranks;
    }

    // ---------------------------------------------------------------------------------------
    // Table construction.
    // ---------------------------------------------------------------------------------------

    /** Dense rank of a strength key: 1 for the strongest hand, {@link #distinctRanks} for the weakest. */
    private static short denseRank(int[] strongestFirst, int key) {
        int lo = 0;
        int hi = strongestFirst.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int value = strongestFirst[mid];
            if (value == key) return (short) (mid + 1);
            if (value > key) lo = mid + 1;
            else hi = mid - 1;
        }
        throw new IllegalStateException("strength key absent from the five-card enumeration: " + key);
    }

    private short[] buildFlushRanks(int[] strengthKeys) {
        short[] table = new short[1 << rankCount];
        for (int mask = 0; mask < table.length; mask++) {
            int cards = Integer.bitCount(mask);
            if (cards >= 5 && cards <= MAX_CARDS) table[mask] = denseRank(strengthKeys, flushStrengthKey(mask));
        }
        return table;
    }

    /**
     * Five-card entries come straight from the rules; six- and seven-card entries take the strongest
     * (smallest) rank over every way of discarding one card, which by induction is the best five.
     */
    private short[] buildNonFlushRanks(int[] strengthKeys) {
        short[] table = new short[sizeOffsets[MAX_CARDS + 1]];
        int[] counts = new int[rankCount];
        fillFiveCardRanks(table, strengthKeys, counts, rankCount - 1, 5);
        for (int cards = 6; cards <= MAX_CARDS; cards++) {
            fillFromOneFewerCard(table, counts, rankCount - 1, cards, cards);
        }
        return table;
    }

    private void fillFiveCardRanks(short[] table, int[] strengthKeys, int[] counts, int rank, int remaining) {
        if (remaining == 0) {
            table[quinaryIndex(counts, 5)] = denseRank(strengthKeys, nonFlushStrengthKey(counts));
            return;
        }
        if (rank < 0) return;
        for (int count = 0; count <= Math.min(4, remaining); count++) {
            counts[rank] = count;
            fillFiveCardRanks(table, strengthKeys, counts, rank - 1, remaining - count);
        }
        counts[rank] = 0;
    }

    private void fillFromOneFewerCard(short[] table, int[] counts, int rank, int remaining, int cardCount) {
        if (remaining == 0) {
            short best = Short.MAX_VALUE;
            for (int r = 0; r < rankCount; r++) {
                if (counts[r] == 0) continue;
                counts[r]--;
                best = (short) Math.min(best, table[quinaryIndex(counts, cardCount - 1)]);
                counts[r]++;
            }
            table[quinaryIndex(counts, cardCount)] = best;
            return;
        }
        if (rank < 0) return;
        for (int count = 0; count <= Math.min(4, remaining); count++) {
            counts[rank] = count;
            fillFromOneFewerCard(table, counts, rank - 1, remaining - count, cardCount);
        }
        counts[rank] = 0;
    }

    /**
     * {@code ways[r][k]} counts the rank-count vectors over the {@code r} lowest ranks that hold
     * {@code k} cards, no rank appearing more than four times.
     */
    private static int[][] waysToFill(int rankCount) {
        int[][] ways = new int[rankCount + 1][CARD_SLOTS];
        ways[0][0] = 1;
        for (int ranks = 1; ranks <= rankCount; ranks++) {
            for (int cards = 0; cards <= MAX_CARDS; cards++) {
                for (int count = 0; count <= Math.min(4, cards); count++) {
                    ways[ranks][cards] += ways[ranks - 1][cards - count];
                }
            }
        }
        return ways;
    }

    private static int[] cumulativeWays(int[][] waysBelow) {
        int rankCount = waysBelow.length - 1;
        int[] flat = new int[rankCount * CARD_SLOTS * 5];
        for (int rank = 0; rank < rankCount; rank++) {
            for (int remaining = 0; remaining < CARD_SLOTS; remaining++) {
                int running = 0;
                for (int count = 0; count <= 4; count++) {
                    flat[(rank * CARD_SLOTS + remaining) * 5 + count] = running;
                    if (remaining - count >= 0) running += waysBelow[rank][remaining - count];
                }
            }
        }
        return flat;
    }

    /** Start of each hand size's block in {@link #nonFlushRanks}; the last entry is the total size. */
    private static int[] sizeOffsets(int[] waysOverAllRanks) {
        int[] offsets = new int[MAX_CARDS + 2];
        for (int cards = 5; cards <= MAX_CARDS; cards++) {
            offsets[cards + 1] = offsets[cards] + waysOverAllRanks[cards];
        }
        return offsets;
    }

    private static int binomial(int n, int k) {
        long result = 1;
        for (int i = 0; i < k; i++) result = result * (n - i) / (i + 1);
        return (int) result;
    }
}

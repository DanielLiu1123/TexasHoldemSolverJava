package pokersolver.eval;

import java.util.Arrays;

/**
 * The two lookup tables behind {@link HandEvaluator}, derived from the rules of Texas hold'em rather
 * than read from a dictionary file.
 *
 * <p>A hand of five to seven cards resolves down one of two disjoint paths:
 *
 * <ul>
 *   <li><b>Flush.</b> If some suit holds five or more cards, the best five cards are all of that
 *       suit — a straight flush or a flush. (With seven cards, a five-card suit leaves two cards
 *       off-suit, too few to build quads or a full house.) {@link #FLUSH_RANKS} maps that suit's rank
 *       bitmask straight to a rank.
 *   <li><b>Everything else.</b> With no five-card suit, no five-card subset is a flush either, so the
 *       hand is decided entirely by how many cards it holds of each rank. {@link #NON_FLUSH_RANKS}
 *       maps that count vector — via the minimal perfect hash in {@link #CUMULATIVE_WAYS} — to a
 *       rank.
 * </ul>
 *
 * <p>Both tables store the same dense rank: {@code 1} is the strongest hand (a royal flush) and
 * {@link #DISTINCT_RANKS} the weakest, so a smaller rank always means a stronger hand and equal ranks
 * mean a tie. The numbering reproduces the historical dictionary file exactly, which {@code
 * HandEvaluatorGoldenTest} verifies entry by entry.
 *
 * <p>Together the tables cost about 150 KB, against the ~50 MB and 2.6M-entry hash map of the
 * dictionary they replace, and a lookup is a handful of array reads rather than up to 21 hash probes.
 * Building them takes ~23 ms, once, at class initialization.
 */
final class RankTables {

    /** Ranks are packed five to an int, four bits each, so a strength key fits in 24 bits. */
    private static final int RANK_BITS = 4;

    private static final int MAX_CARDS = 7;

    /** Remaining-card slots addressable by {@link #CUMULATIVE_WAYS}: 0..7. */
    private static final int CARD_SLOTS = MAX_CARDS + 1;

    /** Deuce through ace. */
    static final int RANK_COUNT = 13;

    /** The number of distinct hand strengths in hold'em. */
    static final int DISTINCT_RANKS = 7462;

    /** Indexed by a suit's 13-bit rank bitmask (five to seven bits set). */
    static final short[] FLUSH_RANKS;

    /** Indexed by {@link #quinaryIndex}. */
    static final short[] NON_FLUSH_RANKS;

    /**
     * Minimal perfect hash for rank-count vectors, flattened as {@code [(rank * 8 + remaining) * 5 +
     * count]}. Entry {@code (r, k, c)} counts the vectors that place fewer than {@code c} cards on
     * rank {@code r} while filling the remaining {@code k} slots from ranks below it — the standard
     * combinatorial number system, generalized from sets to multisets.
     */
    static final int[] CUMULATIVE_WAYS;

    /** Where each hand size's block of {@link #NON_FLUSH_RANKS} starts, indexed by card count. */
    static final int[] SIZE_OFFSETS;

    /**
     * Straight rank bitmasks, strongest first. The last is the wheel — the ace playing below the
     * deuce — whose tiebreaker is its five-high end, making it the weakest straight.
     */
    private static final int[] STRAIGHT_MASKS = new int[RANK_COUNT - 3];

    private static final int[] STRAIGHT_HIGHS = new int[RANK_COUNT - 3];

    static {
        int size = 0;
        for (int high = RANK_COUNT - 1; high >= 4; high--) {
            STRAIGHT_MASKS[size] = 0b11111 << (high - 4);
            STRAIGHT_HIGHS[size++] = high;
        }
        STRAIGHT_MASKS[size] = (1 << (RANK_COUNT - 1)) | 0b1111; // A-2-3-4-5
        STRAIGHT_HIGHS[size] = 3;

        int[][] waysBelow = waysToFill();
        CUMULATIVE_WAYS = cumulativeWays(waysBelow);
        SIZE_OFFSETS = sizeOffsets(waysBelow[RANK_COUNT]);

        int[] strengthKeys = allFiveCardStrengthKeys();
        if (strengthKeys.length != DISTINCT_RANKS) {
            throw new IllegalStateException(
                    "expected %d distinct hands, enumerated %d".formatted(DISTINCT_RANKS, strengthKeys.length));
        }
        FLUSH_RANKS = buildFlushRanks(strengthKeys);
        NON_FLUSH_RANKS = buildNonFlushRanks(strengthKeys);
    }

    private RankTables() {}

    /**
     * Index of the rank-count vector {@code counts} (one entry per rank, each 0..4, summing to
     * {@code cardCount}) within the block for hands of that size.
     */
    static int quinaryIndex(int[] counts, int cardCount) {
        int index = SIZE_OFFSETS[cardCount];
        int remaining = cardCount;
        for (int rank = RANK_COUNT - 1; rank >= 0 && remaining > 0; rank--) {
            int count = counts[rank];
            index += CUMULATIVE_WAYS[(rank * CARD_SLOTS + remaining) * 5 + count];
            remaining -= count;
        }
        return index;
    }

    // ---------------------------------------------------------------------------------------
    // Strength keys: a total order over five-card hands, before they collapse to dense ranks.
    // ---------------------------------------------------------------------------------------

    /**
     * A comparable strength key: the category's strength, then up to five tiebreaker ranks in the
     * order the category compares them (quad rank before kicker, trips rank before its two kickers,
     * and so on). Larger keys are stronger hands.
     */
    private static int strengthKey(HandCategory category, int... tiebreakers) {
        int key = category.strength();
        for (int slot = 0; slot < 5; slot++) {
            int rank = slot < tiebreakers.length ? tiebreakers[slot] + 1 : 0;
            key = (key << RANK_BITS) | rank;
        }
        return key;
    }

    /** Every distinct five-card strength key, strongest first. */
    private static int[] allFiveCardStrengthKeys() {
        int[] keys = new int[binomial(RANK_COUNT, 5) + (SIZE_OFFSETS[6] - SIZE_OFFSETS[5])];
        int size = 0;
        for (int mask = 0; mask < (1 << RANK_COUNT); mask++) {
            if (Integer.bitCount(mask) == 5) keys[size++] = flushStrengthKey(mask);
        }
        size = collectNonFlushKeys(new int[RANK_COUNT], RANK_COUNT - 1, 5, keys, size);

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
    private static int collectNonFlushKeys(int[] counts, int rank, int remaining, int[] keys, int size) {
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
    private static int flushStrengthKey(int suitMask) {
        for (int i = 0; i < STRAIGHT_MASKS.length; i++) {
            if ((suitMask & STRAIGHT_MASKS[i]) == STRAIGHT_MASKS[i]) {
                return strengthKey(HandCategory.STRAIGHT_FLUSH, STRAIGHT_HIGHS[i]);
            }
        }
        return strengthKey(HandCategory.FLUSH, topRanks(suitMask));
    }

    /**
     * The strength key of the five cards described by {@code counts}, evaluated as a non-flush hand.
     * The counts must sum to five.
     */
    private static int nonFlushStrengthKey(int[] counts) {
        // Tiebreakers come out in the order every category compares them, which is exactly
        // (count descending, rank descending): quad before kicker, trips before pair, and so on.
        int[] ordered = new int[5];
        int size = 0;
        for (int count = 4; count >= 1; count--) {
            for (int rank = RANK_COUNT - 1; rank >= 0; rank--) {
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
    private static int straightOrHighCardKey(int[] descendingRanks) {
        int mask = 0;
        for (int rank : descendingRanks) mask |= 1 << rank;
        for (int i = 0; i < STRAIGHT_MASKS.length; i++) {
            if (mask == STRAIGHT_MASKS[i]) return strengthKey(HandCategory.STRAIGHT, STRAIGHT_HIGHS[i]);
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

    /** Dense rank of a strength key: 1 for the strongest hand, {@link #DISTINCT_RANKS} for the weakest. */
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

    private static short[] buildFlushRanks(int[] strengthKeys) {
        short[] table = new short[1 << RANK_COUNT];
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
    private static short[] buildNonFlushRanks(int[] strengthKeys) {
        short[] table = new short[SIZE_OFFSETS[MAX_CARDS + 1]];
        int[] counts = new int[RANK_COUNT];
        fillFiveCardRanks(table, strengthKeys, counts, RANK_COUNT - 1, 5);
        for (int cards = 6; cards <= MAX_CARDS; cards++) {
            fillFromOneFewerCard(table, counts, RANK_COUNT - 1, cards, cards);
        }
        return table;
    }

    private static void fillFiveCardRanks(short[] table, int[] strengthKeys, int[] counts, int rank, int remaining) {
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

    private static void fillFromOneFewerCard(short[] table, int[] counts, int rank, int remaining, int cardCount) {
        if (remaining == 0) {
            short best = Short.MAX_VALUE;
            for (int r = 0; r < RANK_COUNT; r++) {
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
    private static int[][] waysToFill() {
        int[][] ways = new int[RANK_COUNT + 1][CARD_SLOTS];
        ways[0][0] = 1;
        for (int ranks = 1; ranks <= RANK_COUNT; ranks++) {
            for (int cards = 0; cards <= MAX_CARDS; cards++) {
                for (int count = 0; count <= Math.min(4, cards); count++) {
                    ways[ranks][cards] += ways[ranks - 1][cards - count];
                }
            }
        }
        return ways;
    }

    private static int[] cumulativeWays(int[][] waysBelow) {
        int[] flat = new int[RANK_COUNT * CARD_SLOTS * 5];
        for (int rank = 0; rank < RANK_COUNT; rank++) {
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

    /** Start of each hand size's block in {@link #NON_FLUSH_RANKS}; the last entry is the total size. */
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

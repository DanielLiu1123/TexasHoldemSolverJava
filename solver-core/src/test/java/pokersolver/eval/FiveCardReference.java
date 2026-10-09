package pokersolver.eval;

import java.util.Arrays;

/**
 * A deliberately naive five-card hand evaluator, used as the oracle for {@link HandEvaluator}.
 *
 * <p>This exists to be <em>obviously</em> correct rather than fast. It materializes the five cards,
 * sorts them, counts ranks, and reads the category straight off those counts — the way a person
 * would score a hand by hand. It shares no machinery with the evaluator under test: no bitmasks, no
 * flush table, no perfect hash, no six-to-five induction.
 *
 * <p>{@link #keysStrongestFirst()} enumerates all C(52,5) = 2,598,960 hands and sorts their strength
 * keys, so each distinct key gets a dense rank from 1 (royal flush) to 7462 (seven-high). That
 * numbering is the evaluator's published contract, and {@code HandEvaluatorGoldenTest} asserts the two
 * agree on every hand.
 *
 * <p>It replaces a 50 MB dictionary file of precomputed ranks, which served the same purpose but
 * could not be checked, only trusted.
 */
final class FiveCardReference {

    static final int HAND_COUNT = 2_598_960;

    static final int DISTINCT_RANKS = 7462;

    private FiveCardReference() {}

    /** Strength categories, weakest first — the ordering hold'em plays them in. */
    private static final int HIGH_CARD = 0;

    private static final int ONE_PAIR = 1;
    private static final int TWO_PAIR = 2;
    private static final int TRIPS = 3;
    private static final int STRAIGHT = 4;
    private static final int FLUSH = 5;
    private static final int FULL_HOUSE = 6;
    private static final int QUADS = 7;
    private static final int STRAIGHT_FLUSH = 8;

    /**
     * A comparable strength key for five cards, larger being stronger: the category, then the ranks
     * that break ties within it, in the order the rules compare them.
     *
     * @param cards five distinct card ids in {@code [0, 52)}
     */
    static long strengthKey(int[] cards) {
        if (cards.length != 5) throw new IllegalArgumentException("expected five cards");

        int[] ranks = new int[5];
        int[] suits = new int[5];
        for (int i = 0; i < 5; i++) {
            ranks[i] = cards[i] / 4;
            suits[i] = cards[i] % 4;
        }

        boolean flush = suits[0] == suits[1] && suits[1] == suits[2] && suits[2] == suits[3] && suits[3] == suits[4];

        int[] counts = new int[13];
        for (int rank : ranks) counts[rank]++;

        // Distinct ranks, sorted by (count descending, rank descending): quad before kicker, trips
        // before pair, and so on — exactly the order every category compares them in.
        int[] ordered = new int[5];
        int size = 0;
        for (int count = 4; count >= 1; count--) {
            for (int rank = 12; rank >= 0; rank--) {
                if (counts[rank] == count) ordered[size++] = rank;
            }
        }
        int[] tiebreakers = Arrays.copyOf(ordered, size);

        int category;
        if (size == 5) {
            int straightHigh = straightHigh(tiebreakers);
            if (straightHigh >= 0) {
                category = flush ? STRAIGHT_FLUSH : STRAIGHT;
                tiebreakers = new int[] {straightHigh};
            } else {
                category = flush ? FLUSH : HIGH_CARD;
            }
        } else if (size == 4) {
            category = ONE_PAIR;
        } else if (size == 3) {
            category = counts[tiebreakers[0]] == 3 ? TRIPS : TWO_PAIR;
        } else {
            category = counts[tiebreakers[0]] == 4 ? QUADS : FULL_HOUSE;
        }

        long key = category;
        for (int slot = 0; slot < 5; slot++) {
            key = (key << 4) | (slot < tiebreakers.length ? tiebreakers[slot] + 1 : 0);
        }
        return key;
    }

    /**
     * The straight's high rank, or {@code -1} if the five distinct ranks are not consecutive. The
     * wheel (A-2-3-4-5) is a straight, and the ace plays low, so its high card is the five.
     */
    private static int straightHigh(int[] descendingRanks) {
        boolean run = true;
        for (int i = 1; i < 5; i++) {
            if (descendingRanks[i] != descendingRanks[i - 1] - 1) run = false;
        }
        if (run) return descendingRanks[0];
        // A(12) 5(3) 4(2) 3(1) 2(0), descending.
        boolean wheel = descendingRanks[0] == 12
                && descendingRanks[1] == 3
                && descendingRanks[2] == 2
                && descendingRanks[3] == 1
                && descendingRanks[4] == 0;
        return wheel ? 3 : -1;
    }

    /**
     * Dense rank per strength key: 1 for the strongest hand, {@link #DISTINCT_RANKS} for the weakest.
     * The returned array is the distinct keys, strongest first; a key's rank is its index plus one.
     */
    static long[] keysStrongestFirst() {
        long[] keys = new long[HAND_COUNT];
        int size = 0;
        int[] hand = new int[5];
        for (hand[0] = 0; hand[0] < 52; hand[0]++) {
            for (hand[1] = hand[0] + 1; hand[1] < 52; hand[1]++) {
                for (hand[2] = hand[1] + 1; hand[2] < 52; hand[2]++) {
                    for (hand[3] = hand[2] + 1; hand[3] < 52; hand[3]++) {
                        for (hand[4] = hand[3] + 1; hand[4] < 52; hand[4]++) {
                            keys[size++] = strengthKey(hand);
                        }
                    }
                }
            }
        }
        if (size != HAND_COUNT) throw new IllegalStateException("enumerated " + size + " hands");

        long[] distinct = Arrays.stream(keys).distinct().toArray();
        Arrays.sort(distinct);
        for (int i = 0, j = distinct.length - 1; i < j; i++, j--) {
            long swap = distinct[i];
            distinct[i] = distinct[j];
            distinct[j] = swap;
        }
        return distinct;
    }

    /** The dense rank of {@code key} within {@code strongestFirst}, 1-based. */
    static int denseRank(long[] strongestFirst, long key) {
        int lo = 0;
        int hi = strongestFirst.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (strongestFirst[mid] == key) return mid + 1;
            if (strongestFirst[mid] > key) lo = mid + 1;
            else hi = mid - 1;
        }
        throw new IllegalStateException("key not enumerated: " + key);
    }
}

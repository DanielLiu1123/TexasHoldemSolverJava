package pokersolver;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import pokersolver.eval.HandEvaluator;
import pokersolver.ranges.PrivateCards;
import pokersolver.ranges.RiverRange;

/**
 * Caches each player's range projected onto a complete board — the hands the board leaves live, each
 * ranked and sorted — so the showdown kernel never re-evaluates a board it has already seen.
 *
 * <p>A river solve sees one board, a turn solve 47, a flop solve 1081. Every one of them is visited
 * on every CFR iteration by every worker thread, so this cache absorbs the whole cost of hand
 * evaluation and has to be correct under concurrent reads and misses. It used to be a plain {@code
 * HashMap} written from {@link pokersolver.solver.ParallelCfrSolver}'s ForkJoin workers.
 *
 * <p>Two threads that miss on the same board both compute it and race to publish; the loser's arrays
 * are discarded. Projection is a pure function of the board and the range, so the duplicated work is
 * harmless — and unlike {@code computeIfAbsent}, publishing this way never parks a ForkJoin worker
 * on a bin lock.
 */
public final class RiverRangeManager {

    private final ConcurrentMap<Long, RiverRange> playerRanges = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, RiverRange> opponentRanges = new ConcurrentHashMap<>();

    /** The hands of {@code player}'s {@code range} that {@code board} does not block, weakest first. */
    public RiverRange getRiverRange(int player, PrivateCards[] range, long board) {
        ConcurrentMap<Long, RiverRange> cache =
                switch (player) {
                    case 0 -> playerRanges;
                    case 1 -> opponentRanges;
                    default -> throw new IllegalArgumentException("unknown player: " + player);
                };
        RiverRange cached = cache.get(board);
        if (cached != null) return cached;

        RiverRange computed = project(range, board);
        RiverRange published = cache.putIfAbsent(board, computed);
        return published != null ? published : computed;
    }

    private RiverRange project(PrivateCards[] range, long board) {
        // One primitive sort instead of an object sort over a Comparable: pack the inverted rank
        // into the high word so ascending order is weakest-first, and the hand index into the low
        // word so equally-ranked hands keep their order in the range. That tie order fixes the
        // order of the showdown kernel's floating-point accumulation, and hence its exact output.
        long[] ranked = new long[range.length];
        int size = 0;
        for (int hand = 0; hand < range.length; hand++) {
            PrivateCards combo = range[hand];
            if (Card.boardsHasIntercept(combo.mask(), board)) continue;
            long inverted = (long) Integer.MAX_VALUE - HandEvaluator.rank(combo.mask(), board);
            ranked[size++] = (inverted << 32) | hand;
        }
        Arrays.sort(ranked, 0, size);

        int[] ranks = new int[size];
        int[] card1 = new int[size];
        int[] card2 = new int[size];
        int[] reachIndex = new int[size];
        for (int i = 0; i < size; i++) {
            int hand = (int) ranked[i];
            ranks[i] = Integer.MAX_VALUE - (int) (ranked[i] >>> 32);
            card1[i] = range[hand].card1;
            card2[i] = range[hand].card2;
            reachIndex[i] = hand;
        }
        return new RiverRange(ranks, card1, card2, reachIndex);
    }
}

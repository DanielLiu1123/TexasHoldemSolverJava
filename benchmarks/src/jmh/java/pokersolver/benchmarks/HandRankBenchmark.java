package pokersolver.benchmarks;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import pokersolver.eval.HandEvaluator;
import pokersolver.eval.PokerVariant;

/**
 * Measures {@link HandEvaluator#rank} — the inner loop of every showdown evaluation during CFR
 * training, and of every board a solve's range cache projects a range onto.
 *
 * <p>The seven-card lookup this replaced took the best of 21 five-card probes into a 2.6M-entry hash
 * map, each one a likely cache miss. This one is four bit-gathers, a popcount, and two array reads
 * into ~150 KB of tables.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings("NullAway.Init") // JMH state fields are initialized in @Setup
public class HandRankBenchmark {

    HandEvaluator holdem;
    HandEvaluator shortDeck;

    /** Every hole-card pair the river board leaves free, as a seven-card mask: C(47, 2) = 1081. */
    long[] holdemSevenCard;

    long[] shortDeckSevenCard;
    long fiveCardBoard;
    int next;

    @Setup(Level.Trial)
    public void setup() {
        holdem = HandEvaluator.forVariant(PokerVariant.HOLDEM);
        shortDeck = HandEvaluator.forVariant(PokerVariant.SHORT_DECK);

        long board = 0;
        for (int card : SolverFixtures.boardInts(SolverFixtures.RIVER_BOARD)) board |= 1L << card;
        fiveCardBoard = board;
        holdemSevenCard = sevenCardHands(board, 0);

        // Short-deck cards start at the six: rank index 4, card id 16.
        long shortBoard = 0;
        for (int card : SolverFixtures.boardInts("Kd,Jd,Td,7s,8s")) shortBoard |= 1L << card;
        shortDeckSevenCard = sevenCardHands(shortBoard, 16);
    }

    /** The board plus every unblocked hole-card pair drawn from cards {@code >= lowestCard}. */
    private static long[] sevenCardHands(long board, int lowestCard) {
        long[] hands = new long[1081];
        int size = 0;
        for (int a = lowestCard; a < 52; a++) {
            if ((board & (1L << a)) != 0) continue;
            for (int b = a + 1; b < 52; b++) {
                if ((board & (1L << b)) != 0) continue;
                hands[size++] = board | (1L << a) | (1L << b);
            }
        }
        return java.util.Arrays.copyOf(hands, size);
    }

    @Benchmark
    public int sevenCardRank() {
        next = (next + 1) % holdemSevenCard.length;
        return holdem.rank(holdemSevenCard[next]);
    }

    @Benchmark
    public int fiveCardRank() {
        return holdem.rank(fiveCardBoard);
    }

    @Benchmark
    public int shortDeckSevenCardRank() {
        next = (next + 1) % shortDeckSevenCard.length;
        return shortDeck.rank(shortDeckSevenCard[next]);
    }
}

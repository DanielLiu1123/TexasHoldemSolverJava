package pokersolver.benchmarks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import pokersolver.compairer.Compairer;

/**
 * Measures {@link Compairer#get_rank} lookup cost — the inner loop of every showdown evaluation
 * during CFR training.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings("NullAway.Init") // JMH state fields are initialized in @Setup
public class HandRankBenchmark {

    Compairer compairer;
    int[] board;
    int[][] privateCombos;
    int next;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        compairer = SolverFixtures.holdemCompairer();
        board = SolverFixtures.boardInts(SolverFixtures.RIVER_BOARD);

        // All hole-card combos not blocked by the board: C(47, 2) = 1081.
        // Card ints are (rank-2)*4+suit, contiguous in [0, 52) for the standard deck.
        boolean[] blocked = new boolean[52];
        for (int card : board) blocked[card] = true;
        List<int[]> combos = new ArrayList<>();
        for (int a = 0; a < 52; a++) {
            if (blocked[a]) continue;
            for (int b = a + 1; b < 52; b++) {
                if (blocked[b]) continue;
                combos.add(new int[] {a, b});
            }
        }
        privateCombos = combos.toArray(new int[0][]);
        next = 0;
    }

    @Benchmark
    public int rankLookup() {
        int[] combo = privateCombos[next];
        next = (next + 1) % privateCombos.length;
        return compairer.getRank(combo, board);
    }
}

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
import pokersolver.Deck;
import pokersolver.GameTree;

/** Measures full game-tree construction for the benchmark scenario. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings("NullAway.Init") // JMH state fields are initialized in @Setup
public class TreeBuildingBenchmark {

    Deck deck;

    @Setup(Level.Trial)
    public void setup() {
        deck = SolverFixtures.holdemDeck();
    }

    @Benchmark
    public GameTree buildTurnTree() {
        return SolverFixtures.buildTree(deck, SolverFixtures.ROUND_TURN);
    }

    @Benchmark
    public GameTree buildRiverTree() {
        return SolverFixtures.buildTree(deck, SolverFixtures.ROUND_RIVER);
    }
}

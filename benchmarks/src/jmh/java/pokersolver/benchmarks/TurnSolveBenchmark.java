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
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.SolverConfig;
import pokersolver.trainable.DiscountedCfrTrainable;
import pokersolver.utils.PrivateRangeConverter;

/**
 * Measures a fixed-iteration turn solve with the parallel solver. Turn trees include a chance node
 * per river card, so this exercises the chance-node fan-out that dominates pre-river solving.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
public class TurnSolveBenchmark {

    static final int CFR_ITERATIONS = 10;

    @State(Scope.Benchmark)
    @SuppressWarnings("NullAway.Init") // JMH state fields are initialized in @Setup
    public static class Shared {
        Deck deck;
        int[] board;
        PrivateCards[] ipRange;
        PrivateCards[] oopRange;

        @Setup(Level.Trial)
        public void setup() {
            deck = SolverFixtures.holdemDeck();
            board = SolverFixtures.boardInts(SolverFixtures.TURN_BOARD);
            ipRange = PrivateRangeConverter.rangeStr2Cards(SolverFixtures.IP_RANGE, board);
            oopRange = PrivateRangeConverter.rangeStr2Cards(SolverFixtures.OOP_RANGE, board);
        }
    }

    @State(Scope.Thread)
    @SuppressWarnings("NullAway.Init") // JMH state fields are initialized in @Setup
    public static class FreshTree {
        GameTree tree;

        @Setup(Level.Invocation)
        public void setup(Shared shared) {
            tree = SolverFixtures.buildTree(shared.deck, SolverFixtures.ROUND_TURN);
        }
    }

    @Benchmark
    public GameTree parallel(Shared shared, FreshTree fresh) throws Exception {
        SolverConfig config = SolverConfig.builder()
                .tree(fresh.tree)
                .range1(shared.ipRange)
                .range2(shared.oopRange)
                .initialBoard(shared.board)
                .deck(shared.deck)
                .iterationNumber(CFR_ITERATIONS)
                .printInterval(CFR_ITERATIONS)
                .logfile(null)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .build();
        ParallelCfrPlusSolver solver = new ParallelCfrPlusSolver(config, -1, 1.0, 0.0, 1, 0);
        solver.train();
        return solver.getTree();
    }
}

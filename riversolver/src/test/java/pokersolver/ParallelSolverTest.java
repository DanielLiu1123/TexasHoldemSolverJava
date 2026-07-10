package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pokersolver.solver.Algorithm;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.Solver;
import pokersolver.solver.SolverConfig;
import tools.jackson.databind.JsonNode;

/**
 * The parallel solver must agree with the single-threaded one.
 *
 * <p>CFR's tree traversal is embarrassingly parallel only if the state it shares is genuinely
 * read-only. It was not: {@code RiverRangeManager} cached each board's ranked range in a plain
 * {@code HashMap} that every ForkJoin worker wrote to, and the Monte Carlo sampler kept the
 * iteration's sampled card in a mutable field on the solver. Neither is a bug you can find by
 * reading a stack trace afterwards — a corrupted {@code HashMap} bucket surfaces as a wrong strategy
 * or an infinite loop, arbitrarily far away. The repeated equality check below is what makes it
 * visible.
 */
class ParallelSolverTest {

    private static final int ITERATIONS = 40;

    private static SolverConfig.Builder scenario() {
        return SolverFixture.builder(
                SolverFixture.RIVER_TREE,
                SolverFixture.RIVER_BOARD,
                SolverFixture.WIDE_RANGE,
                Algorithm.DISCOUNTED_CFR,
                ITERATIONS);
    }

    private static JsonNode solveAndDump(Solver solver) throws Exception {
        solver.train();
        return solver.getTree().dumps().get("strategy").get("strategy");
    }

    @RepeatedTest(3)
    void theParallelSolverAgreesWithTheSingleThreadedOne() throws Exception {
        JsonNode single = solveAndDump(new CfrPlusRiverSolver(scenario().build()));
        JsonNode parallel = solveAndDump(new ParallelCfrPlusSolver(scenario().build(), 8, 1.0, 1.0, 1, 0));

        // Float addition is not associative, so ForkJoin's non-deterministic summation order at
        // chance nodes moves the last bits. The strategies must still agree to well within
        // anything a poker player could act on.
        assertThat(parallel.propertyNames()).containsExactlyElementsOf(iterable(single.propertyNames()));
        single.propertyNames().forEach(hand -> {
            JsonNode expected = single.get(hand);
            JsonNode actual = parallel.get(hand);
            assertThat(actual.size()).as("action count for %s", hand).isEqualTo(expected.size());
            for (int action = 0; action < expected.size(); action++) {
                assertThat((float) actual.get(action).asDouble())
                        .as("parallel strategy for %s action %d", hand, action)
                        .isCloseTo((float) expected.get(action).asDouble(), within(2e-3f));
            }
        });
    }

    @Test
    void manyThreadsRacingTheRiverRangeCacheStillAgree() throws Exception {
        // The range cache is populated on the first showdown a worker reaches; with one board and
        // many workers, every worker misses at once. A HashMap here loses entries or spins forever.
        JsonNode reference = solveAndDump(new CfrPlusRiverSolver(scenario().build()));
        int threads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        JsonNode raced = solveAndDump(new ParallelCfrPlusSolver(scenario().build(), threads, 1.0, 1.0, 1, 0));

        raced.propertyNames().forEach(hand -> {
            JsonNode expected = reference.get(hand);
            assertThat(expected)
                    .as("hand %s present in the reference solve", hand)
                    .isNotNull();
            for (int action = 0; action < expected.size(); action++) {
                assertThat((float) raced.get(hand).get(action).asDouble())
                        .as("raced strategy for %s action %d", hand, action)
                        .isCloseTo((float) expected.get(action).asDouble(), within(2e-3f));
            }
        });
    }

    @Test
    void concurrentSolvesDoNotInterfere() throws Exception {
        JsonNode reference = solveAndDump(new CfrPlusRiverSolver(scenario().build()));

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<JsonNode>> solves = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                solves.add(
                        () -> solveAndDump(new ParallelCfrPlusSolver(scenario().build(), 2, 1.0, 1.0, 1, 0)));
            }
            for (Future<JsonNode> result : pool.invokeAll(solves)) {
                JsonNode strategy = result.get();
                strategy.propertyNames().forEach(hand -> {
                    for (int action = 0; action < reference.get(hand).size(); action++) {
                        assertThat((float) strategy.get(hand).get(action).asDouble())
                                .as("concurrent solve, hand %s action %d", hand, action)
                                .isCloseTo(
                                        (float) reference.get(hand).get(action).asDouble(), within(2e-3f));
                    }
                });
            }
        }
    }

    @Test
    void theSingleThreadedSolverIsBitForBitDeterministic() throws Exception {
        String first = solveAndDump(new CfrPlusRiverSolver(scenario().build())).toString();
        String second = solveAndDump(new CfrPlusRiverSolver(scenario().build())).toString();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void invalidThreadCountsAndForkProbabilitiesAreRejected() {
        assertThatThrownBy(() -> new ParallelCfrPlusSolver(scenario().build(), 0, 1.0, 1.0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParallelCfrPlusSolver(scenario().build(), -2, 1.0, 1.0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParallelCfrPlusSolver(scenario().build(), 2, 1.5, 1.0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParallelCfrPlusSolver(scenario().build(), 2, 1.0, -0.1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trainingStopsWhenAskedTo() throws Exception {
        Solver solver = new CfrPlusRiverSolver(SolverFixture.builder(
                        SolverFixture.RIVER_TREE,
                        SolverFixture.RIVER_BOARD,
                        SolverFixture.NARROW_RANGE,
                        Algorithm.DISCOUNTED_CFR,
                        1_000_000)
                .printInterval(1)
                .progressListener((iteration, exploitability, elapsedMs) -> {})
                .build());
        Thread trainer = new Thread(() -> {
            try {
                solver.train();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        trainer.start();
        Thread.sleep(200);
        solver.requestStop();
        trainer.join(30_000);
        assertThat(trainer.isAlive()).as("train() returned after requestStop()").isFalse();
    }

    private static Iterable<String> iterable(Iterable<String> names) {
        List<String> list = new ArrayList<>();
        names.forEach(list::add);
        return list;
    }
}

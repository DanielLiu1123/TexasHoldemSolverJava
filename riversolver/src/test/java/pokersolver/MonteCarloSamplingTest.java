package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pokersolver.solver.Algorithm;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.Solver;

/**
 * Public chance sampling: a chance node deals one sampled card instead of all of them, and every
 * chance node in the same betting round must deal the <em>same</em> card, or the two players' reach
 * probabilities describe different boards.
 *
 * <p>That agreement used to live in a mutable {@code int[] roundDeal} field on the solver, written
 * by whichever ForkJoin worker reached a chance node first. The draw is now made once per iteration
 * and passed down the recursion, so there is nothing for the workers to race on.
 *
 * <p>Sampling makes each iteration cheap and noisy, so these tests check that training runs, stays
 * finite, and makes progress — not that it converges to a particular number.
 */
class MonteCarloSamplingTest {

    private static final int ITERATIONS = 60;

    private static List<Float> traceOf(Solver solver, List<Float> trace) throws Exception {
        solver.train();
        return trace;
    }

    private static pokersolver.solver.SolverConfig.Builder sampledTurnSolve(List<Float> trace) {
        return SolverFixture.builder(
                        SolverFixture.shortDeckTurn(),
                        SolverFixture.TURN_BOARD,
                        SolverFixture.SHORT_DECK_RANGE,
                        Algorithm.DISCOUNTED_CFR,
                        ITERATIONS)
                .monteCarloAlg(MonteCarloAlg.PUBLIC)
                .printInterval(10)
                .progressListener((iteration, exploitability, elapsedMs) -> trace.add(exploitability));
    }

    @Test
    void sampledTrainingProducesFiniteExploitability() throws Exception {
        List<Float> trace = new ArrayList<>();
        traceOf(new CfrPlusRiverSolver(sampledTurnSolve(trace).build()), trace);

        assertThat(trace).isNotEmpty();
        assertThat(trace).allSatisfy(value -> {
            assertThat(Float.isNaN(value)).as("exploitability is a number").isFalse();
            assertThat(Float.isInfinite(value)).as("exploitability is finite").isFalse();
            assertThat(value).isPositive();
        });
        assertThat(trace.getLast())
                .as("sampling still makes progress: %s", trace)
                .isLessThan(trace.getFirst());
    }

    @Test
    void sampledTrainingIsSafeUnderForkJoin() throws Exception {
        // Every worker walks the same sampled card per round. A shared mutable draw would let one
        // player's traversal deal a different turn card than the other's.
        List<Float> trace = new ArrayList<>();
        traceOf(new ParallelCfrPlusSolver(sampledTurnSolve(trace).build(), 8, 1.0, 1.0, 1, 0), trace);

        assertThat(trace).isNotEmpty();
        assertThat(trace).allSatisfy(value -> assertThat(Float.isNaN(value)).isFalse());
        assertThat(trace.getLast()).isPositive();
    }

    @Test
    void sampledTrainingOnARiverSolveIsIdenticalToFullEnumeration() throws Exception {
        // A river tree has no chance nodes left to sample, so PUBLIC must be a no-op there.
        List<Float> sampled = new ArrayList<>();
        new CfrPlusRiverSolver(SolverFixture.builder(
                                SolverFixture.shortDeckRiver(),
                                SolverFixture.RIVER_BOARD,
                                SolverFixture.SHORT_DECK_RANGE,
                                Algorithm.DISCOUNTED_CFR,
                                40)
                        .monteCarloAlg(MonteCarloAlg.PUBLIC)
                        .progressListener((iteration, exploitability, elapsedMs) -> sampled.add(exploitability))
                        .build())
                .train();

        List<Float> full = new ArrayList<>();
        new CfrPlusRiverSolver(SolverFixture.builder(
                                SolverFixture.shortDeckRiver(),
                                SolverFixture.RIVER_BOARD,
                                SolverFixture.SHORT_DECK_RANGE,
                                Algorithm.DISCOUNTED_CFR,
                                40)
                        .monteCarloAlg(MonteCarloAlg.NONE)
                        .progressListener((iteration, exploitability, elapsedMs) -> full.add(exploitability))
                        .build())
                .train();

        assertThat(sampled).isEqualTo(full);
    }
}

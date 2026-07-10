package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pokersolver.solver.Algorithm;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.SolverConfig;

/**
 * Every CFR variant must actually converge, and the ones that claim to be faster must actually be
 * faster.
 *
 * <p>Exploitability — what a best-responding opponent could extract, as a percentage of the pot — is
 * the only measure that matters here; it reaches zero at a Nash equilibrium. These bounds are what
 * caught three defects in the trainables: vanilla CFR was serializing its (non-convergent) current
 * strategy, CFR+ was averaging raw regrets instead of the strategies they induce, and Discounted CFR
 * was discounting the current iteration's regret along with the history it meant to forget.
 *
 * <p>Ceilings are set ~40% above what {@code AlgorithmBakeoff} measures, so they fail on a
 * regression rather than on noise.
 */
class SolverConvergenceTest {

    private static final int ITERATIONS = 100;

    /** Exploitability ceiling per variant on the wide-range river, as a percentage of the pot. */
    private static final Map<Algorithm, Float> CEILING = new EnumMap<>(Algorithm.class);

    static {
        CEILING.put(Algorithm.CFR, 0.15f);
        CEILING.put(Algorithm.CFR_PLUS, 0.06f);
        CEILING.put(Algorithm.PCFR_PLUS, 0.055f);
        CEILING.put(Algorithm.PDCFR_PLUS, 0.035f);
        CEILING.put(Algorithm.PDCFR, 0.025f);
        CEILING.put(Algorithm.DISCOUNTED_CFR, 0.02f);
    }

    private static float solve(Algorithm algorithm, int iterations) throws Exception {
        return SolverFixture.solveAndMeasure(
                SolverFixture.builder(
                        SolverFixture.RIVER_TREE,
                        SolverFixture.RIVER_BOARD,
                        SolverFixture.WIDE_RANGE,
                        algorithm,
                        iterations),
                false);
    }

    @ParameterizedTest(name = "{0} converges on a river solve")
    @EnumSource(Algorithm.class)
    void everyVariantConvergesOnARiverSolve(Algorithm algorithm) throws Exception {
        assertThat(solve(algorithm, ITERATIONS))
                .as("%s exploitability after %d iterations (%% of pot)", algorithm.id(), ITERATIONS)
                .isPositive()
                .isLessThan(CEILING.get(algorithm));
    }

    @Test
    void eachRefinementBeatsTheVariantItRefines() throws Exception {
        Map<Algorithm, Float> measured = new EnumMap<>(Algorithm.class);
        for (Algorithm algorithm : Algorithm.values()) measured.put(algorithm, solve(algorithm, ITERATIONS));

        assertThat(measured.get(Algorithm.CFR_PLUS))
                .as("regret-matching+ beats vanilla CFR: %s", measured)
                .isLessThan(measured.get(Algorithm.CFR));
        assertThat(measured.get(Algorithm.DISCOUNTED_CFR))
                .as("discounting beats plain RM+: %s", measured)
                .isLessThan(measured.get(Algorithm.CFR_PLUS));
        assertThat(measured.get(Algorithm.PDCFR))
                .as("PDCFR's signed accumulator beats PDCFR+'s clipped one: %s", measured)
                .isLessThan(measured.get(Algorithm.PDCFR_PLUS));
        assertThat(measured.get(Algorithm.DISCOUNTED_CFR))
                .as("the default is the best variant on this scenario: %s", measured)
                .isLessThan(measured.get(Algorithm.PDCFR));
    }

    @Test
    void exploitabilityFallsByAnOrderOfMagnitude() throws Exception {
        List<Float> trace = new ArrayList<>();
        SolverConfig config = SolverFixture.builder(
                        SolverFixture.RIVER_TREE,
                        SolverFixture.RIVER_BOARD,
                        SolverFixture.NARROW_RANGE,
                        Algorithm.DISCOUNTED_CFR,
                        ITERATIONS)
                .progressListener((iteration, exploitability, elapsedMs) -> trace.add(exploitability))
                .build();
        new CfrPlusRiverSolver(config).train();

        assertThat(trace).hasSizeGreaterThan(3);
        assertThat(trace.getLast())
                .as("final vs first exploitability: %s", trace)
                .isLessThan(trace.getFirst() / 100);
    }

    @Test
    void aTurnSolveConverges() throws Exception {
        float exploitability = SolverFixture.solveAndMeasure(
                SolverFixture.builder(
                        SolverFixture.TURN_TREE,
                        SolverFixture.TURN_BOARD,
                        SolverFixture.NARROW_RANGE,
                        Algorithm.DISCOUNTED_CFR,
                        ITERATIONS),
                false);
        assertThat(exploitability).isPositive().isLessThan(0.25f);
    }

    @Test
    void aBroadwayHeavyRangeConverges() throws Exception {
        float exploitability = SolverFixture.solveAndMeasure(
                SolverFixture.builder(
                        SolverFixture.RIVER_TREE,
                        SolverFixture.RIVER_BOARD,
                        "AA,KK,QQ,JJ,TT,99,88,77,AK,AQ,AJ,KQ,KJ,QJ,JT,T9,98",
                        Algorithm.DISCOUNTED_CFR,
                        ITERATIONS),
                false);
        assertThat(exploitability).isPositive().isLessThan(0.03f);
    }

    @Test
    void theParallelSolverConvergesToo() throws Exception {
        float exploitability = SolverFixture.solveAndMeasure(
                SolverFixture.builder(
                        SolverFixture.RIVER_TREE,
                        SolverFixture.RIVER_BOARD,
                        SolverFixture.WIDE_RANGE,
                        Algorithm.DISCOUNTED_CFR,
                        ITERATIONS),
                true);
        assertThat(exploitability).isPositive().isLessThan(CEILING.get(Algorithm.DISCOUNTED_CFR));
    }
}

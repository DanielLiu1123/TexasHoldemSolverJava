package pokersolver;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pokersolver.solver.Algorithm;

/**
 * Manual: prints an exploitability table across variants, scenarios, and iteration counts.
 *
 * <p>Not a test — a measurement. It picked the default variant and set the ceilings in {@link
 * SolverConvergenceTest}, and the table in {@link Algorithm}'s javadoc is its output. Re-run it by
 * removing {@code @Disabled} before changing either.
 */
@Disabled("manual: prints a comparison table")
class AlgorithmBakeoff {

    private record Scenario(String name, String tree, int[] board, String range) {}

    @Test
    void compareVariantsAcrossScenarios() throws Exception {
        Scenario[] scenarios = {
            new Scenario(
                    "river (narrow range)",
                    SolverFixture.RIVER_TREE,
                    SolverFixture.RIVER_BOARD,
                    SolverFixture.NARROW_RANGE),
            new Scenario(
                    "river (wide range)",
                    SolverFixture.RIVER_TREE,
                    SolverFixture.RIVER_BOARD,
                    SolverFixture.WIDE_RANGE),
            new Scenario(
                    "river (broadway range)",
                    SolverFixture.RIVER_TREE,
                    SolverFixture.RIVER_BOARD,
                    "AA,KK,QQ,JJ,TT,99,88,77,AK,AQ,AJ,KQ,KJ,QJ,JT,T9,98"),
            new Scenario("turn", SolverFixture.TURN_TREE, SolverFixture.TURN_BOARD, SolverFixture.NARROW_RANGE),
        };
        // 50-200 is where a solve actually runs. 800 shows the asymptotic behaviour, where the
        // optimistic variants' tighter regret bound starts to tell and pcfr_plus nearly catches
        // discounted_cfr — and where pdcfr_plus's bump at 200 resolves as oscillation, not divergence.
        int[] iterationCounts = {50, 100, 200, 800};

        for (Scenario scenario : scenarios) {
            System.out.printf("%n=== %s ===%n", scenario.name());
            System.out.printf("%-16s", "iterations");
            for (int iterations : iterationCounts) System.out.printf("%14d", iterations);
            System.out.println();

            Map<Algorithm, double[]> results = new LinkedHashMap<>();
            for (Algorithm algorithm : Algorithm.values()) {
                double[] row = new double[iterationCounts.length];
                for (int i = 0; i < iterationCounts.length; i++) {
                    row[i] = SolverFixture.solveAndMeasure(
                            SolverFixture.builder(
                                    scenario.tree(), scenario.board(), scenario.range(), algorithm, iterationCounts[i]),
                            false);
                }
                results.put(algorithm, row);
            }
            results.forEach((algorithm, row) -> {
                System.out.printf("%-16s", algorithm.id());
                for (double value : row) System.out.printf("%14.6f", value);
                System.out.println();
            });
        }
    }
}

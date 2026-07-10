package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pokersolver.solver.Algorithm;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.SequentialCfrSolver;
import pokersolver.solver.Solver;
import tools.jackson.databind.JsonNode;

/**
 * Pins each CFR variant's solved strategy on a fixed river spot.
 *
 * <p>The single-threaded solver under {@link MonteCarloAlg#NONE} is deterministic, so the root
 * node's average strategy is reproducible to the bit — which makes it a tripwire for silent changes
 * in the SIMD kernels, the hand evaluator, and the tree builder. It is not a correctness proof:
 * {@link SolverConvergenceTest} owns that, by checking exploitability actually falls.
 *
 * <p>The root is the out-of-position player's check/bet decision; each hand's strategy is {@code
 * [P(check), P(bet)]}. A deliberate strategy change must regenerate these values with {@link
 * #printGoldenForCapture()} — but only after {@code SolverConvergenceTest} says the change is an
 * improvement.
 */
class StrategyRegressionTest {

    private static final int ITERATIONS = 120;
    private static final float TOLERANCE = 5e-3f;

    private static final String[] HANDS = {
        "AsAh", "KsKh", "QsQh", "JsJh", "TsTh", "9s9h", "AsKs", "AsQs", "KsQs", "JsTs"
    };

    /** Representative hands -> golden P(check) at the root, per algorithm. P(bet) is the complement. */
    private static final Map<Algorithm, Map<String, Float>> GOLDEN = new LinkedHashMap<>();

    static {
        // Regenerated for the 52-card deck after short-deck support was removed. The scenario's
        // tree, board and range are unchanged; only the hand ranks moved, because short-deck ranked
        // a flush above a full house and trips above a straight.
        GOLDEN.put(
                Algorithm.CFR,
                golden(0.9896f, 0.0239f, 0.0138f, 0.9975f, 0.9875f, 0.5612f, 0.9990f, 0.0537f, 0.9770f, 0.6455f));
        GOLDEN.put(
                Algorithm.CFR_PLUS,
                golden(0.9984f, 0.0049f, 0.0022f, 0.9907f, 0.9925f, 0.6056f, 0.9766f, 0.0302f, 0.9868f, 0.6214f));
        GOLDEN.put(
                Algorithm.PCFR_PLUS,
                golden(0.9996f, 0.0019f, 0.0006f, 0.9973f, 0.9924f, 0.6146f, 0.9775f, 0.0216f, 0.9869f, 0.6225f));
        GOLDEN.put(
                Algorithm.PDCFR_PLUS,
                golden(0.9999f, 0.0008f, 0.0014f, 0.9955f, 0.9928f, 0.6466f, 0.9765f, 0.0095f, 0.9861f, 0.6196f));
        GOLDEN.put(
                Algorithm.PDCFR,
                golden(0.9999f, 0.0031f, 0.0016f, 0.9979f, 0.9950f, 0.6446f, 0.9749f, 0.0097f, 0.9864f, 0.6186f));
        GOLDEN.put(
                Algorithm.DISCOUNTED_CFR,
                golden(0.9995f, 0.0007f, 0.0008f, 0.9989f, 0.9974f, 0.6548f, 0.9751f, 0.0055f, 0.9859f, 0.6154f));
    }

    private static Map<String, Float> golden(float... pCheck) {
        Map<String, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < HANDS.length; i++) map.put(HANDS[i], pCheck[i]);
        return map;
    }

    /** Solves single-threaded and returns the root action node's average strategy by hand. */
    private static Map<String, float[]> solve(Algorithm algorithm) throws Exception {
        Solver solver = new SequentialCfrSolver(SolverFixture.builder(
                        SolverFixture.RIVER_TREE,
                        SolverFixture.RIVER_BOARD,
                        SolverFixture.NARROW_RANGE,
                        algorithm,
                        ITERATIONS)
                .build());
        solver.train();

        JsonNode strategy = solver.getTree().dumps().get("strategy").get("strategy");
        Map<String, float[]> byHand = new LinkedHashMap<>();
        strategy.propertyNames().forEach(hand -> {
            JsonNode probabilities = strategy.get(hand);
            float[] values = new float[probabilities.size()];
            for (int i = 0; i < values.length; i++)
                values[i] = (float) probabilities.get(i).asDouble();
            byHand.put(hand, values);
        });
        return byHand;
    }

    @ParameterizedTest(name = "{0} reproduces its golden root strategy")
    @EnumSource(Algorithm.class)
    void eachAlgorithmMatchesItsGoldenStrategy(Algorithm algorithm) throws Exception {
        Map<String, float[]> actual = solve(algorithm);
        Objects.requireNonNull(GOLDEN.get(algorithm)).forEach((hand, expectedCheck) -> {
            float[] got = Objects.requireNonNull(actual.get(hand), () -> hand + " missing from the solved strategy");
            assertThat(got).as("%s should be a check/bet decision", hand).hasSize(2);
            assertThat(got[0])
                    .as("%s: P(check | %s)", algorithm.id(), hand)
                    .isCloseTo(expectedCheck, within(TOLERANCE));
            assertThat(got[1])
                    .as("%s: P(bet | %s)", algorithm.id(), hand)
                    .isCloseTo(1 - expectedCheck, within(TOLERANCE));
        });
    }

    @Test
    void everyHandsStrategyIsAProbabilityDistribution() throws Exception {
        solve(Algorithm.DISCOUNTED_CFR).forEach((hand, probabilities) -> {
            float sum = 0;
            for (float p : probabilities) {
                assertThat(p).as("P(action | %s)", hand).isBetween(0f, 1f);
                sum += p;
            }
            assertThat(sum).as("strategy for %s sums to one", hand).isCloseTo(1f, within(1e-4f));
        });
    }

    /** Regenerates the golden table above. Run manually after a deliberate strategy change. */
    @Disabled("manual: prints golden values for capture")
    @Test
    void printGoldenForCapture() throws Exception {
        for (Algorithm algorithm : Algorithm.values()) {
            Map<String, float[]> strategy = solve(algorithm);
            StringBuilder row = new StringBuilder();
            for (String hand : HANDS) row.append("%.4ff, ".formatted(Objects.requireNonNull(strategy.get(hand))[0]));
            System.out.printf("GOLDEN.put(Algorithm.%s, golden(%s));%n", algorithm.name(), row);
        }
    }
}

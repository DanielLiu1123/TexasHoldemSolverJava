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
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.Solver;
import tools.jackson.databind.JsonNode;

/**
 * Pins each CFR variant's solved strategy on a fixed short-deck river spot.
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
        // Regenerated after the trainable fixes. PCFR+ is unchanged from the pre-refactor
        // baseline, bit for bit — it is the one variant whose semantics this work did not touch,
        // which is what pins the hand evaluator, the range layout, and the SIMD kernels as exactly
        // equivalent. The other five moved because their bugs were fixed (see Algorithm's table).
        GOLDEN.put(
                Algorithm.CFR,
                golden(0.9315f, 0.0001f, 0.0098f, 0.9335f, 0.9486f, 0.5897f, 0.9991f, 0.0535f, 0.9863f, 0.5949f));
        GOLDEN.put(
                Algorithm.CFR_PLUS,
                golden(0.9969f, 0.0022f, 0.0020f, 0.9585f, 0.9666f, 0.6343f, 0.9704f, 0.0174f, 0.9870f, 0.6360f));
        GOLDEN.put(
                Algorithm.PCFR_PLUS,
                golden(0.9995f, 0.0028f, 0.0004f, 0.9881f, 0.9917f, 0.6147f, 0.9734f, 0.0194f, 0.9870f, 0.6225f));
        GOLDEN.put(
                Algorithm.PDCFR_PLUS,
                golden(0.9999f, 0.0029f, 0.0013f, 0.9983f, 0.9986f, 0.5719f, 0.9727f, 0.0360f, 0.9880f, 0.6263f));
        GOLDEN.put(
                Algorithm.PDCFR,
                golden(0.9997f, 0.0032f, 0.0014f, 0.9985f, 0.9987f, 0.6204f, 0.9749f, 0.0179f, 0.9867f, 0.6180f));
        GOLDEN.put(
                Algorithm.DISCOUNTED_CFR,
                golden(0.9992f, 0.0009f, 0.0008f, 0.9992f, 0.9993f, 0.6518f, 0.9745f, 0.0064f, 0.9856f, 0.6141f));
    }

    private static Map<String, Float> golden(float... pCheck) {
        Map<String, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < HANDS.length; i++) map.put(HANDS[i], pCheck[i]);
        return map;
    }

    /** Solves single-threaded and returns the root action node's average strategy by hand. */
    private static Map<String, float[]> solve(Algorithm algorithm) throws Exception {
        Solver solver = new CfrPlusRiverSolver(SolverFixture.builder(
                        SolverFixture.shortDeckRiver(),
                        SolverFixture.RIVER_BOARD,
                        SolverFixture.SHORT_DECK_RANGE,
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

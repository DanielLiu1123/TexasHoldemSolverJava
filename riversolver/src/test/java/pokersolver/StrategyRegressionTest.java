package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pokersolver.compairer.Compairer;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.Algorithm;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.Solver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;
import tools.jackson.databind.JsonNode;

/**
 * Locks the solved strategy of each CFR variant on a fixed shortdeck river scenario.
 * Single-thread {@link CfrPlusRiverSolver} with {@link MonteCarloAlg#NONE} is deterministic, so
 * the root node's average strategy is reproducible — this guards the SIMD kernels and the
 * algorithm code against silent regressions, and is the safety net for the planned tree-builder
 * overhaul.
 *
 * <p>Golden values were captured from a known-good run (see {@link #printGoldenForCapture()}); a
 * deliberate strategy change must regenerate them. The root is OOP's (player 1) check/bet
 * decision; each hand's strategy is {@code [P(check), P(bet)]}.
 */
public class StrategyRegressionTest {

    static final String RANGE = "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT";
    static final int ITERATIONS = 120;
    static final float TOL = 5e-3f;
    static final int[] BOARD = new int[] {
        Card.strCard2int("Kd"), Card.strCard2int("Jd"),
        Card.strCard2int("Td"), Card.strCard2int("7s"),
        Card.strCard2int("8s")
    };

    /** Representative hands -> golden [P(check), P(bet)] at the root, per algorithm. */
    static final Map<Algorithm, Map<String, float[]>> GOLDEN = new EnumMap<>(Algorithm.class);
    /** Final-ish exploitability ceiling per algorithm (% of the pot). */
    static final Map<Algorithm, Float> EXPLOITABILITY_CEILING = new EnumMap<>(Algorithm.class);

    static {
        GOLDEN.put(Algorithm.CFR, golden(new Object[][] {
            {"AsAh", 1.0000f}, {"KsKh", 0.0000f}, {"QsQh", 0.0000f}, {"JsJh", 1.0000f}, {"TsTh", 1.0000f},
            {"9s9h", 0.4699f}, {"AsKs", 1.0000f}, {"AsQs", 0.0000f}, {"KsQs", 1.0000f}, {"JsTs", 0.1323f}
        }));
        GOLDEN.put(Algorithm.CFR_PLUS, golden(new Object[][] {
            {"AsAh", 1.0000f}, {"KsKh", 0.0000f}, {"QsQh", 0.0000f}, {"JsJh", 1.0000f}, {"TsTh", 1.0000f},
            {"9s9h", 0.6750f}, {"AsKs", 0.9746f}, {"AsQs", 0.0000f}, {"KsQs", 0.9801f}, {"JsTs", 0.6158f}
        }));
        GOLDEN.put(Algorithm.DISCOUNTED_CFR, golden(new Object[][] {
            {"AsAh", 1.0000f}, {"KsKh", 0.0000f}, {"QsQh", 0.0000f}, {"JsJh", 1.0000f}, {"TsTh", 1.0000f},
            {"9s9h", 0.6716f}, {"AsKs", 0.9742f}, {"AsQs", 0.0000f}, {"KsQs", 0.9857f}, {"JsTs", 0.6094f}
        }));
        GOLDEN.put(Algorithm.PCFR_PLUS, golden(new Object[][] {
            {"AsAh", 0.9995f}, {"KsKh", 0.0028f}, {"QsQh", 0.0004f}, {"JsJh", 0.9881f}, {"TsTh", 0.9917f},
            {"9s9h", 0.6147f}, {"AsKs", 0.9734f}, {"AsQs", 0.0194f}, {"KsQs", 0.9870f}, {"JsTs", 0.6225f}
        }));

        EXPLOITABILITY_CEILING.put(Algorithm.CFR, 0.6f);
        EXPLOITABILITY_CEILING.put(Algorithm.CFR_PLUS, 0.05f);
        EXPLOITABILITY_CEILING.put(Algorithm.DISCOUNTED_CFR, 0.02f);
        EXPLOITABILITY_CEILING.put(Algorithm.PCFR_PLUS, 0.05f);
    }

    /** Builds a hand -> [P(check), P(bet)] map from {hand, P(check)} rows (bet = 1 - check). */
    private static Map<String, float[]> golden(Object[][] rows) {
        Map<String, float[]> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            float check = (float) row[1];
            map.put((String) row[0], new float[] {check, 1f - check});
        }
        return map;
    }

    static Config config;
    static Compairer compairer;
    static Deck deck;

    @BeforeAll
    static void loadEnvironments() throws Exception {
        ClassLoader cl = StrategyRegressionTest.class.getClassLoader();
        config = new Config(
                new File(cl.getResource("yamls/rule_shortdeck_simple.yaml").getFile()).getAbsolutePath());
        compairer = SolverEnvironment.compairerFromConfig(config);
        deck = SolverEnvironment.deckFromConfig(config);
    }

    record Solved(float exploitability, JsonNode rootStrategy) {}

    /** Solves single-threaded and returns the root action node's average strategy by hand. */
    private static Solved solve(Algorithm algorithm) throws Exception {
        PrivateCards[] range1 = PrivateRangeConverter.rangeStr2Cards(RANGE, BOARD);
        PrivateCards[] range2 = PrivateRangeConverter.rangeStr2Cards(RANGE, BOARD);
        float[] last = {Float.NaN};
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(SolverEnvironment.gameTreeFromConfig(config, deck))
                .range1(range1)
                .range2(range2)
                .initialBoard(BOARD)
                .compairer(compairer)
                .deck(deck)
                .iterationNumber(ITERATIONS)
                .printInterval(10) // evaluate periodically; the last callback ~ final convergence
                .algorithm(algorithm)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .progressListener((iteration, exploitability, elapsedMs) -> last[0] = exploitability)
                .build());
        solver.train();
        JsonNode root = solver.getTree().dumps(false);
        return new Solved(last[0], root.get("strategy").get("strategy"));
    }

    /** Strategy probabilities for each hand, keyed by hand label. */
    private static Map<String, float[]> handStrategy(Solved solved) {
        Map<String, float[]> byHand = new LinkedHashMap<>();
        JsonNode strat = solved.rootStrategy;
        strat.propertyNames().forEach(hand -> {
            JsonNode probs = strat.get(hand);
            float[] arr = new float[probs.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = (float) probs.get(i).asDouble();
            byHand.put(hand, arr);
        });
        return byHand;
    }

    @Test
    public void eachAlgorithmConvergesAndMatchesGolden() throws Exception {
        for (Algorithm algorithm : Algorithm.values()) {
            Solved solved = solve(algorithm);
            assertThat(solved.exploitability)
                    .as("%s exploitability (%% pot)", algorithm.id())
                    .isLessThan(Objects.requireNonNull(EXPLOITABILITY_CEILING.get(algorithm)));

            Map<String, float[]> actual = handStrategy(solved);
            Objects.requireNonNull(GOLDEN.get(algorithm)).forEach((hand, expected) -> {
                float[] got = Objects.requireNonNull(actual.get(hand), () -> hand + " missing from strategy");
                assertThat(got)
                        .as("%s root strategy for %s", algorithm.id(), hand)
                        .hasSameSizeAs(expected);
                for (int i = 0; i < expected.length; i++) {
                    assertThat(got[i])
                            .as("%s root strategy for %s action %s", algorithm.id(), hand, i)
                            .isCloseTo(expected[i], within(TOL));
                }
            });
        }
    }

    @Test
    public void singleThreadSolveIsDeterministic() throws Exception {
        Solved a = solve(Algorithm.DISCOUNTED_CFR);
        Solved b = solve(Algorithm.DISCOUNTED_CFR);
        assertThat(b.exploitability).isEqualTo(a.exploitability);
        assertThat(b.rootStrategy.toString()).isEqualTo(a.rootStrategy.toString());
    }

    /** Regenerates the golden table above; run manually after a deliberate strategy change. */
    @Disabled("manual: prints golden values for capture")
    @Test
    public void printGoldenForCapture() throws Exception {
        for (Algorithm algorithm : Algorithm.values()) {
            Solved s = solve(algorithm);
            System.out.printf("%n=== %s (exploitability %.6f%% pot) ===%n", algorithm.id(), s.exploitability);
            handStrategy(s).forEach((hand, probs) -> {
                StringBuilder sb = new StringBuilder();
                for (float p : probs) sb.append(String.format("%.4f ", p));
                System.out.printf("  %s -> %s%n", hand, sb.toString().trim());
            });
        }
    }
}

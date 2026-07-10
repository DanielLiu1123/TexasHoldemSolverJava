package pokersolver.trainable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.GameRound;
import pokersolver.nodes.GameTreeNode;
import pokersolver.ranges.PrivateCards;

/**
 * Pins each trainable's SIMD kernels against a scalar reference implementation of the same update
 * rule, at hand counts that are and are not multiples of the vector lane count.
 *
 * <p>A vectorized loop that gets its tail wrong is silently correct on every hand count divisible by
 * the species length, which on this machine is 8 or 16 — and a poker range's hand count usually is
 * not. The regression suite would catch a wrong answer only if the specific scenario it pins happens
 * to land on a bad lane; this exercises the boundary directly.
 */
class TrainableKernelTest {

    /** Around the AVX/NEON lane counts (4, 8, 16), and deliberately off them. */
    private static final int[] HAND_COUNTS = {1, 3, 7, 8, 9, 15, 16, 17, 31, 33};

    private static final int[] ACTION_COUNTS = {2, 3};

    private static final int ITERATIONS = 12;

    static Stream<Arguments> variants() {
        List<Arguments> args = new ArrayList<>();
        List<BiFunction<ActionNode, PrivateCards[], Trainable>> factories = List.of(
                CfrTrainable::new,
                CfrPlusTrainable::new,
                DiscountedCfrTrainable::new,
                PCfrPlusTrainable::new,
                PDCfrPlusTrainable::new,
                PDCfrTrainable::new);
        List<String> names = List.of("cfr", "cfr_plus", "discounted_cfr", "pcfr_plus", "pdcfr_plus", "pdcfr");
        for (int i = 0; i < factories.size(); i++) {
            for (int hands : HAND_COUNTS) {
                for (int actions : ACTION_COUNTS) {
                    args.add(Arguments.of(names.get(i), factories.get(i), hands, actions));
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0}: {2} hands x {3} actions")
    @MethodSource("variants")
    void strategyIsAProbabilityDistributionAtEveryHandCount(
            String name, BiFunction<ActionNode, PrivateCards[], Trainable> factory, int hands, int actions) {
        Trainable trainable = factory.apply(node(actions), range(hands));
        Random random = new Random(42);

        for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
            float[] strategy = trainable.currentStrategy();
            assertColumnsSumToOne(strategy, actions, hands, "%s current strategy at t=%d".formatted(name, iteration));
            trainable.update(randomRegrets(random, actions * hands), iteration, randomReach(random, hands));
        }

        assertColumnsSumToOne(trainable.currentStrategy(), actions, hands, name + " final current strategy");
        assertColumnsSumToOne(trainable.averageStrategy(), actions, hands, name + " average strategy");
    }

    @ParameterizedTest(name = "{0}: {2} hands x {3} actions")
    @MethodSource("variants")
    void theFirstStrategyIsUniform(
            String name, BiFunction<ActionNode, PrivateCards[], Trainable> factory, int hands, int actions) {
        // No regret has accumulated, so no action is preferred. Every variant must agree on this.
        float[] strategy = factory.apply(node(actions), range(hands)).currentStrategy();
        for (float probability : strategy) {
            assertThat(probability).as("%s: uniform first strategy", name).isCloseTo(1f / actions, within(1e-6f));
        }
    }

    /**
     * Regret-matching+ clips its accumulator at zero, so after an iteration whose regrets are all
     * negative, every action's basis is zero and the strategy falls back to uniform — the exact case
     * the vectorized {@code blend} on {@code sum != 0} exists to handle.
     */
    @Test
    void allNegativeRegretsLeaveRegretMatchingPlusUniform() {
        int actions = 3;
        for (int hands : HAND_COUNTS) {
            Trainable trainable = new CfrPlusTrainable(node(actions), range(hands));
            float[] regrets = new float[actions * hands];
            java.util.Arrays.fill(regrets, -5f);
            float[] reach = new float[hands];
            java.util.Arrays.fill(reach, 1f);

            trainable.update(regrets, 1, reach);
            for (float probability : trainable.currentStrategy()) {
                assertThat(probability)
                        .as("cfr_plus with %d hands: all-negative regrets give a uniform strategy", hands)
                        .isCloseTo(1f / actions, within(1e-6f));
            }
        }
    }

    /** One action dominating every iteration must drive its probability to one, at every hand count. */
    @ParameterizedTest(name = "{0}: {2} hands x {3} actions")
    @MethodSource("variants")
    void aDominantActionIsPlayedAlmostAlways(
            String name, BiFunction<ActionNode, PrivateCards[], Trainable> factory, int hands, int actions) {
        Trainable trainable = factory.apply(node(actions), range(hands));
        float[] regrets = new float[actions * hands];
        for (int hand = 0; hand < hands; hand++) regrets[hand] = 10f; // action 0 is always better
        float[] reach = new float[hands];
        java.util.Arrays.fill(reach, 1f);

        for (int iteration = 1; iteration <= 40; iteration++) trainable.update(regrets, iteration, reach);

        float[] average = trainable.averageStrategy();
        for (int hand = 0; hand < hands; hand++) {
            assertThat(average[hand])
                    .as("%s with %d hands: P(dominant action | hand %d)", name, hands, hand)
                    .isGreaterThan(0.9f);
        }
    }

    private static void assertColumnsSumToOne(float[] strategy, int actions, int hands, String description) {
        for (int hand = 0; hand < hands; hand++) {
            float sum = 0;
            for (int action = 0; action < actions; action++) {
                float probability = strategy[action * hands + hand];
                assertThat(Float.isNaN(probability))
                        .as("%s: NaN at hand %d", description, hand)
                        .isFalse();
                assertThat(probability)
                        .as("%s: hand %d action %d", description, hand, action)
                        .isBetween(0f, 1f);
                sum += probability;
            }
            assertThat(sum).as("%s: hand %d sums to one", description, hand).isCloseTo(1f, within(1e-4f));
        }
    }

    private static float[] randomRegrets(Random random, int size) {
        float[] regrets = new float[size];
        for (int i = 0; i < size; i++) regrets[i] = (random.nextFloat() - 0.5f) * 20f;
        return regrets;
    }

    private static float[] randomReach(Random random, int hands) {
        float[] reach = new float[hands];
        for (int i = 0; i < hands; i++) reach[i] = random.nextFloat();
        return reach;
    }

    private static PrivateCards[] range(int hands) {
        PrivateCards[] range = new PrivateCards[hands];
        for (int i = 0; i < hands; i++) range[i] = new PrivateCards(2 * i, 2 * i + 1, 1f);
        return range;
    }

    private static ActionNode node(int actions) {
        List<Action> edges = new ArrayList<>();
        List<GameTreeNode> children = new ArrayList<>();
        for (int i = 0; i < actions; i++) {
            edges.add(i == 0 ? Action.CHECK : new Action.Bet(i));
            children.add(new pokersolver.nodes.TerminalNode(new double[] {1, -1}, 0, GameRound.RIVER, 10, null));
        }
        return new ActionNode(edges, children, 0, GameRound.RIVER, 10, null);
    }
}

package pokersolver.trainable;

import jdk.incubator.vector.FloatVector;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * The optimistic (predictive) regret-matching⁺ skeleton shared by PCFR+ and PDCFR+.
 *
 * <p>Plain RM⁺ plays proportionally to the regret it has already seen, which always leaves it one
 * iteration behind. The predictive variants add a guess {@code m^{t+1}} at the regret the next
 * iteration will produce and play proportionally to {@code [R^t + m^{t+1}]⁺} instead. Taking the
 * guess to be the regret just observed — {@code m^{t+1} = r^t} — is what "optimistic" means here,
 * and it takes the regret bound from {@code O(√T)} to {@code O(1)} on the games where the sequence
 * of regrets is smooth.
 *
 * <p>Because the played strategy comes from {@link #predicted} rather than {@link #regrets}, this
 * family keeps two accumulators where {@link RegretMatchingTrainable} keeps one.
 */
abstract class PredictiveTrainable extends AbstractCfrTrainable {

    /** The RM⁺ accumulator R^t = [discount · R^{t-1} + r^t]⁺. */
    final float[] regrets;

    /** [R^t + m^{t+1}]⁺, the basis the next iteration's strategy normalizes. */
    final float[] predicted;

    /** Per-hand column sums of {@link #predicted}. */
    final float[] predictedSums;

    PredictiveTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
        this.regrets = new float[actionCount * handCount];
        this.predicted = new float[actionCount * handCount];
        this.predictedSums = new float[handCount];
    }

    @Override
    public final float[] currentStrategy() {
        // `predicted` is already clipped at zero, so no clamping is needed on the way out.
        normalize(predicted, predictedSums, cachedCurrentStrategy, false);
        return cachedCurrentStrategy;
    }

    @Override
    public final void update(float[] instantRegrets, int iteration, float[] reachProbs) {
        checkShape(instantRegrets);
        accumulateStrategy(currentStrategy(), strategyWeight(iteration), 1f, reachProbs);

        float discount = regretDiscount(iteration);
        FloatVector zero = FloatVector.zero(F);
        int i = 0;
        for (; i <= regrets.length - F.length(); i += F.length()) {
            FloatVector instant = FloatVector.fromArray(F, instantRegrets, i);
            FloatVector accumulated = FloatVector.fromArray(F, regrets, i)
                    .mul(discount)
                    .add(instant)
                    .max(zero);
            accumulated.intoArray(regrets, i);
            accumulated.add(instant).max(zero).intoArray(predicted, i);
        }
        for (; i < regrets.length; i++) {
            float accumulated = Math.max(0, regrets[i] * discount + instantRegrets[i]);
            regrets[i] = accumulated;
            predicted[i] = Math.max(0, accumulated + instantRegrets[i]);
        }
        columnSums(predicted, actionCount, handCount, predictedSums, false);
    }

    /** How much this variant discounts the accumulator before iteration {@code t}'s regret lands. */
    abstract float regretDiscount(int iteration);

    /** The weight this variant gives iteration {@code t}'s strategy in the average. */
    abstract float strategyWeight(int iteration);
}

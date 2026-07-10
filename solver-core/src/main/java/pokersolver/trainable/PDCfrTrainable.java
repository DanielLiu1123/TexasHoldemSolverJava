package pokersolver.trainable;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Predictive Discounted CFR (Xu, Meng, Yang et al., IJCAI 2024, "Minimizing Weighted Counterfactual
 * Regret with Optimistic Online Mirror Descent"): Discounted CFR's signed, sign-dependently
 * discounted accumulator, played through PCFR+'s optimistic prediction step.
 *
 * <pre>
 *   R^t     = R^{t-1} · (t-1)^α / ((t-1)^α + 1) + r^t      when R^{t-1} &gt; 0
 *   R^t     = R^{t-1} · (t-1)^β / ((t-1)^β + 1) + r^t      otherwise
 *   σ^{t+1} ∝ [ R^t + r^t ]⁺                                (prediction m^{t+1} = r^t)
 *   C^t     = C^{t-1} · ((t-1)/t)^γ + π^t · σ^t
 * </pre>
 *
 * <p>The paper's other combination, {@link PDCfrPlusTrainable}, keeps regret-matching⁺'s clipped
 * accumulator. That clipping is what makes it weaker here: with negative regret discarded outright,
 * DCFR's β has nothing left to discount, and β = 0 — halving negative regret rather than erasing it
 * — is where most of DCFR's edge on poker subgames comes from. Keeping the sign and adding the
 * prediction on top compounds both.
 */
public final class PDCfrTrainable extends AbstractCfrTrainable {

    private static final float ALPHA = 1.5f;
    private static final float BETA = 0f;
    private static final float GAMMA = 2f;

    /** Signed cumulative regret R^t, discounted by sign. */
    private final float[] regrets;

    /** [R^t + m^{t+1}]⁺, the basis the next iteration's strategy normalizes. */
    private final float[] predicted;

    private final float[] predictedSums;

    public PDCfrTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
        this.regrets = new float[actionCount * handCount];
        this.predicted = new float[actionCount * handCount];
        this.predictedSums = new float[handCount];
    }

    /** {@code x^exponent / (x^exponent + 1)}, DCFR's discount factor. Rises to 1 as x grows. */
    private static float discount(float x, float exponent) {
        double power = Math.pow(x, exponent);
        return (float) (power / (power + 1));
    }

    @Override
    public float[] currentStrategy() {
        normalize(predicted, predictedSums, cachedCurrentStrategy, false);
        return cachedCurrentStrategy;
    }

    @Override
    public void update(float[] instantRegrets, int iteration, float[] reachProbs) {
        checkShape(instantRegrets);
        accumulateStrategy(currentStrategy(), 1f, (float) Math.pow((iteration - 1.0) / iteration, GAMMA), reachProbs);

        float previous = iteration - 1;
        float positiveDiscount = discount(previous, ALPHA);
        float negativeDiscount = discount(previous, BETA);

        FloatVector zero = FloatVector.zero(F);
        int i = 0;
        for (; i <= regrets.length - F.length(); i += F.length()) {
            FloatVector instant = FloatVector.fromArray(F, instantRegrets, i);
            FloatVector accumulated = FloatVector.fromArray(F, regrets, i);
            VectorMask<Float> positive = accumulated.compare(VectorOperators.GT, zero);
            accumulated = accumulated
                    .mul(negativeDiscount)
                    .blend(accumulated.mul(positiveDiscount), positive)
                    .add(instant);
            accumulated.intoArray(regrets, i);
            accumulated.add(instant).max(zero).intoArray(predicted, i);
        }
        for (; i < regrets.length; i++) {
            float accumulated = regrets[i];
            accumulated = accumulated * (accumulated > 0 ? positiveDiscount : negativeDiscount) + instantRegrets[i];
            regrets[i] = accumulated;
            predicted[i] = Math.max(0, accumulated + instantRegrets[i]);
        }
        columnSums(predicted, actionCount, handCount, predictedSums, false);
    }
}

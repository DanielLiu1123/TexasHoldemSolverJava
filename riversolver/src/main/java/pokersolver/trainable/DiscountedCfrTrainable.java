package pokersolver.trainable;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Discounted CFR (Brown &amp; Sandholm, AAAI 2019, "Solving Imperfect-Information Games via
 * Discounted Regret Minimization"):
 *
 * <pre>
 *   R^t = R^{t-1} · (t-1)^α / ((t-1)^α + 1) + r^t      when R^{t-1} &gt; 0
 *   R^t = R^{t-1} · (t-1)^β / ((t-1)^β + 1) + r^t      otherwise
 *   C^t = C^{t-1} · ((t-1)/t)^γ + π^t · σ^t
 * </pre>
 *
 * <p>Early iterations are wild — they are played against a nearly uniform opponent — so their regret
 * carries little information. Discounting the accumulator by a factor that rises to 1 lets the
 * algorithm forget them, and discounting positive and negative regret at different rates (α &gt; β)
 * lets an action that has gone bad recover faster than one that was never good. The paper's α = 1.5,
 * β = 0, γ = 2 are used here; β = 0 halves negative regret every iteration.
 *
 * <p>This is the default variant: it converges fastest of the three regret-matching families on
 * poker subgames.
 */
public final class DiscountedCfrTrainable extends RegretMatchingTrainable {

    private static final float ALPHA = 1.5f;
    private static final float BETA = 0f;
    private static final float GAMMA = 2f;

    public DiscountedCfrTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    /** {@code x^exponent / (x^exponent + 1)}, the paper's discount factor. Rises to 1 as x grows. */
    private static float discount(float x, float exponent) {
        double power = Math.pow(x, exponent);
        return (float) (power / (power + 1));
    }

    @Override
    void accumulateRegrets(float[] instantRegrets, int iteration) {
        // The discount depends on the accumulator's sign *before* this iteration's regret lands on
        // it, and this iteration's regret enters at full weight. Discounting (R + r) as one term
        // would shrink the freshest information along with the stale.
        float previous = iteration - 1;
        float positiveDiscount = discount(previous, ALPHA);
        float negativeDiscount = discount(previous, BETA);

        FloatVector zero = FloatVector.zero(F);
        int i = 0;
        for (; i <= regrets.length - F.length(); i += F.length()) {
            FloatVector accumulated = FloatVector.fromArray(F, regrets, i);
            VectorMask<Float> positive = accumulated.compare(VectorOperators.GT, zero);
            accumulated
                    .mul(negativeDiscount)
                    .blend(accumulated.mul(positiveDiscount), positive)
                    .add(FloatVector.fromArray(F, instantRegrets, i))
                    .intoArray(regrets, i);
        }
        for (; i < regrets.length; i++) {
            float accumulated = regrets[i];
            regrets[i] = accumulated * (accumulated > 0 ? positiveDiscount : negativeDiscount) + instantRegrets[i];
        }
    }

    @Override
    float strategyWeight(int iteration) {
        return 1f;
    }

    @Override
    float strategyDecay(int iteration) {
        return (float) Math.pow((iteration - 1.0) / iteration, GAMMA);
    }
}

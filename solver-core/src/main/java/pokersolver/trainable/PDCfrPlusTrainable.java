package pokersolver.trainable;

import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Predictive Discounted CFR+ (Xu, Meng, Yang et al., IJCAI 2024, "Minimizing Weighted Counterfactual
 * Regret with Optimistic Online Mirror Descent").
 *
 * <pre>
 *   R^t     = [ (t^α / (t^α + 1)) · R^{t-1} + r^t ]⁺
 *   σ^{t+1} ∝ [ R^t + r^t ]⁺
 *   C^t     = C^{t-1} + t^γ · π^t · σ^t
 * </pre>
 *
 * <p>The paper's contribution is to show that {@link DiscountedCfrTrainable}'s discounting and
 * {@link PCfrPlusTrainable}'s optimistic prediction are the same object viewed twice — both are
 * instances of optimistic online mirror descent on a <em>weighted</em> regret — and that combining
 * them is therefore principled rather than merely convenient. The combination dominates both parents
 * on the paper's benchmarks, poker subgames included.
 *
 * <p>Only positive regret needs a discount rate here: RM⁺ clips the accumulator at zero, so there is
 * never any negative regret for DCFR's β to act on.
 */
public final class PDCfrPlusTrainable extends PredictiveTrainable {

    private static final float ALPHA = 1.5f;
    private static final float GAMMA = 2f;

    public PDCfrPlusTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    @Override
    float regretDiscount(int iteration) {
        double power = Math.pow(iteration, ALPHA);
        return (float) (power / (power + 1));
    }

    @Override
    float strategyWeight(int iteration) {
        return (float) Math.pow(iteration, GAMMA);
    }
}

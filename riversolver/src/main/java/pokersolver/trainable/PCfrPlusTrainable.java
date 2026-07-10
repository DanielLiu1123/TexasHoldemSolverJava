package pokersolver.trainable;

import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Predictive CFR+ (Farina, Kroer &amp; Sandholm, ICML 2021, "Faster Game Solving via Predictive
 * Blackwell Approachability"): regret-matching⁺ with no discounting, an optimistic prediction step,
 * and the quadratic averaging the paper pairs with it.
 *
 * <p>Fastest of the four on matrix games; on poker subgames the discounted variants pull ahead,
 * because there the early iterations are noisy enough to be worth forgetting — which is exactly what
 * {@link PDCfrPlusTrainable} adds on top of this.
 */
public final class PCfrPlusTrainable extends PredictiveTrainable {

    public PCfrPlusTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    @Override
    float regretDiscount(int iteration) {
        return 1f;
    }

    @Override
    float strategyWeight(int iteration) {
        return (float) iteration * iteration;
    }
}

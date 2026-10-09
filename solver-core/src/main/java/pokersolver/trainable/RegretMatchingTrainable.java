package pokersolver.trainable;

import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * The regret-matching skeleton shared by CFR, CFR+, and Discounted CFR.
 *
 * <p>All three keep one cumulative regret per (action, hand) and play proportionally to its positive
 * part. They differ only in how history is folded into that accumulator — unclipped, clipped at
 * zero, or clipped and discounted — and in how the strategies they play are weighted into the
 * average.
 *
 * <p>The iteration's order matters and is fixed here: the strategy that <em>produced</em> this
 * iteration's regrets is the one accumulated into the average, before the regrets that it produced
 * change the accumulator underneath it. Deriving the strategy first and averaging it afterwards
 * would average σ^{t+1} against the reach probabilities of iteration t.
 */
abstract class RegretMatchingTrainable extends AbstractCfrTrainable {

    /** Cumulative counterfactual regret R^t, per (action, hand). */
    final float[] regrets;

    /** Per-hand Σ_a max(0, R^t(a, h)) — the normalizer of the current strategy. */
    final float[] positiveSums;

    RegretMatchingTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
        this.regrets = new float[actionCount * handCount];
        this.positiveSums = new float[handCount];
    }

    @Override
    public final float[] currentStrategy() {
        normalize(regrets, positiveSums, cachedCurrentStrategy, true);
        return cachedCurrentStrategy;
    }

    @Override
    public final void update(float[] instantRegrets, int iteration, float[] reachProbs) {
        checkShape(instantRegrets);
        accumulateStrategy(currentStrategy(), strategyWeight(iteration), strategyDecay(iteration), reachProbs);
        accumulateRegrets(instantRegrets, iteration);
        columnSums(regrets, actionCount, handCount, positiveSums, true);
    }

    /** Folds {@code instantRegrets} into {@link #regrets} under this variant's discounting rule. */
    abstract void accumulateRegrets(float[] instantRegrets, int iteration);

    /** The weight this variant gives iteration {@code t}'s strategy in the average. */
    abstract float strategyWeight(int iteration);

    /** How much this variant decays the accumulated average before adding iteration {@code t}. */
    float strategyDecay(int iteration) {
        return 1f;
    }
}

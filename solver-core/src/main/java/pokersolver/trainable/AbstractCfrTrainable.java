package pokersolver.trainable;

import static pokersolver.utils.JsonUtil.MAPPER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;
import tools.jackson.databind.node.ObjectNode;

/**
 * What every CFR variant shares: the node's dimensions, the SIMD species, the strategy accumulator
 * whose normalization is the average strategy, and the JSON projection.
 *
 * <p>All four variants average the same way — accumulate {@code weight · π^t · σ^t} into {@link
 * #cumStrategy}, then normalize per hand — and differ only in the weight and in how they turn
 * regrets into {@link #currentStrategy()}. Subclasses own their regret accumulators and supply
 * {@link #update}.
 */
public abstract class AbstractCfrTrainable implements Trainable {

    static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;

    final ActionNode actionNode;
    final PrivateCards[] privateCards;
    final int actionCount;
    final int handCount;

    /** Reused by {@link #currentStrategy()}; never escapes past the next call. */
    final float[] cachedCurrentStrategy;

    /** Σ_t weight(t) · π^t(h) · σ^t(a, h); {@link #averageStrategy()} normalizes it per hand. */
    final float[] cumStrategy;

    protected AbstractCfrTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        this.actionNode = actionNode;
        this.privateCards = privateCards;
        this.actionCount = actionNode.getChildren().size();
        this.handCount = privateCards.length;
        this.cachedCurrentStrategy = new float[actionCount * handCount];
        this.cumStrategy = new float[actionCount * handCount];
    }

    /** Guards {@link #update} against a regret vector shaped for a different node. */
    final void checkShape(float[] regrets) {
        if (regrets.length != actionCount * handCount) {
            throw new IllegalArgumentException(
                    "expected %d regrets, got %d".formatted(actionCount * handCount, regrets.length));
        }
    }

    /**
     * Accumulates {@code strategy}, weighted by {@code weight} and the acting player's reach, into
     * {@link #cumStrategy} after decaying what is already there by {@code decay}.
     *
     * <p>DCFR's {@code ((t-1)/t)^γ} discount is a decay; CFR+'s linear and PCFR+'s quadratic
     * weighting are weights with no decay.
     */
    final void accumulateStrategy(float[] strategy, float weight, float decay, float[] reachProbs) {
        for (int action = 0; action < actionCount; action++) {
            int base = action * handCount;
            int hand = 0;
            for (; hand <= handCount - F.length(); hand += F.length()) {
                int i = base + hand;
                FloatVector reach = FloatVector.fromArray(F, reachProbs, hand);
                FloatVector.fromArray(F, strategy, i)
                        .mul(weight)
                        .fma(reach, FloatVector.fromArray(F, cumStrategy, i).mul(decay))
                        .intoArray(cumStrategy, i);
            }
            for (; hand < handCount; hand++) {
                int i = base + hand;
                cumStrategy[i] = cumStrategy[i] * decay + strategy[i] * weight * reachProbs[hand];
            }
        }
    }

    @Override
    public final float[] averageStrategy() {
        float[] average = new float[actionCount * handCount];
        float uniform = 1F / actionCount;
        for (int hand = 0; hand < handCount; hand++) {
            float sum = 0;
            for (int action = 0; action < actionCount; action++) sum += cumStrategy[action * handCount + hand];
            for (int action = 0; action < actionCount; action++) {
                int i = action * handCount + hand;
                average[i] = sum > 0 ? cumStrategy[i] / sum : uniform;
            }
        }
        return average;
    }

    /**
     * Normalizes {@code basis} per hand into {@code out}, falling back to a uniform strategy for
     * hands whose basis sums to zero (no action has accumulated positive regret yet).
     *
     * @param sums per-hand column sums of {@code basis}
     * @param clampNegative whether {@code basis} may hold negative values that must read as zero
     */
    final void normalize(float[] basis, float[] sums, float[] out, boolean clampNegative) {
        float uniform = 1F / actionCount;
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        FloatVector zero = FloatVector.zero(F);
        for (int action = 0; action < actionCount; action++) {
            int base = action * handCount;
            int hand = 0;
            for (; hand <= handCount - F.length(); hand += F.length()) {
                FloatVector sum = FloatVector.fromArray(F, sums, hand);
                FloatVector value = FloatVector.fromArray(F, basis, base + hand);
                if (clampNegative) value = value.max(zero);
                // Lanes with sum == 0 divide to Inf/NaN, then get blended away.
                uniformV.blend(value.div(sum), sum.compare(VectorOperators.NE, 0f))
                        .intoArray(out, base + hand);
            }
            for (; hand < handCount; hand++) {
                int i = base + hand;
                float value = clampNegative ? Math.max(basis[i], 0) : basis[i];
                out[i] = sums[hand] != 0 ? value / sums[hand] : uniform;
            }
        }
    }

    /** Column sums of {@code values}, clamping negatives to zero when {@code clampNegative}. */
    static void columnSums(float[] values, int actionCount, int handCount, float[] out, boolean clampNegative) {
        Arrays.fill(out, 0);
        FloatVector zero = FloatVector.zero(F);
        for (int action = 0; action < actionCount; action++) {
            int base = action * handCount;
            int hand = 0;
            for (; hand <= handCount - F.length(); hand += F.length()) {
                FloatVector value = FloatVector.fromArray(F, values, base + hand);
                if (clampNegative) value = value.max(zero);
                FloatVector.fromArray(F, out, hand).add(value).intoArray(out, hand);
            }
            for (; hand < handCount; hand++) {
                float value = values[base + hand];
                out[hand] += clampNegative ? Math.max(value, 0) : value;
            }
        }
    }

    @Override
    public final ObjectNode dumps() {
        float[] strategy = averageStrategy();
        List<String> actionLabels = new ArrayList<>(actionCount);
        for (Action action : actionNode.getActions()) actionLabels.add(action.label());

        ObjectNode byHand = MAPPER.createObjectNode();
        for (int hand = 0; hand < handCount; hand++) {
            float[] probabilities = new float[actionCount];
            for (int action = 0; action < actionCount; action++) {
                probabilities[action] = strategy[action * handCount + hand];
            }
            byHand.set(privateCards[hand].toString(), MAPPER.valueToTree(probabilities));
        }

        ObjectNode node = MAPPER.createObjectNode();
        node.set("actions", MAPPER.valueToTree(actionLabels));
        node.set("strategy", byHand);
        return node;
    }
}

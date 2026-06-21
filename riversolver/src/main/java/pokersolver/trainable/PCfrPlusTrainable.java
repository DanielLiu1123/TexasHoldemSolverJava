package pokersolver.trainable;

import java.util.Arrays;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Predictive CFR+ (PCFR+, Farina/Kroer/Sandholm 2021, "Faster Game Solving via Predictive
 * Blackwell Approachability").
 *
 * <p>Regret-matching+ with an optimistic prediction step: cumulative regrets update exactly like
 * RM+ ({@code R = [R + r]+}), but the strategy for the next iteration is proportional to
 * {@code [R + m]+} where the prediction {@code m} is the most recent instantaneous regret vector.
 * The average strategy uses quadratic (t²) weighting, which the paper pairs with PCFR+. Its
 * accumulator shape differs from the RM+ variants, so it extends {@link AbstractCfrTrainable}
 * directly rather than {@link RegretMatchingTrainable}.
 */
public class PCfrPlusTrainable extends AbstractCfrTrainable {

    /** Cumulative clipped regrets R (RM+ accumulator). */
    final float[] rPlus;

    /** Strategy basis [R + m]+ from the latest update; the played strategy normalizes this. */
    final float[] predictedPlus;

    final float[] predictedPlusSum;

    /** Average strategy accumulator: played strategy × reach × t². */
    final float[] cumStrategy;

    public PCfrPlusTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
        this.rPlus = new float[this.actionNumber * this.cardNumber];
        this.predictedPlus = new float[this.actionNumber * this.cardNumber];
        this.predictedPlusSum = new float[this.cardNumber];
        this.cumStrategy = new float[this.actionNumber * this.cardNumber];
    }

    @Override
    protected float[] strategyForDump() {
        return getAverageStrategy();
    }

    @Override
    public float[] getAverageStrategy() {
        float[] retval = new float[this.actionNumber * this.cardNumber];
        for (int privateId = 0; privateId < this.cardNumber; privateId++) {
            float sum = 0;
            for (int actionId = 0; actionId < actionNumber; actionId++) {
                sum += this.cumStrategy[actionId * this.cardNumber + privateId];
            }
            for (int actionId = 0; actionId < actionNumber; actionId++) {
                int index = actionId * this.cardNumber + privateId;
                retval[index] = sum != 0 ? this.cumStrategy[index] / sum : 1F / this.actionNumber;
            }
        }
        return retval;
    }

    @Override
    public float[] getcurrentStrategy() {
        fillCurrentStrategy(cachedCurrentStrategy);
        return cachedCurrentStrategy;
    }

    private void fillCurrentStrategy(float[] out) {
        float uniform = 1F / this.actionNumber;
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                FloatVector sum = FloatVector.fromArray(F, this.predictedPlusSum, hand);
                FloatVector normalized = FloatVector.fromArray(F, this.predictedPlus, base + hand)
                        .div(sum);
                uniformV.blend(normalized, sum.compare(VectorOperators.NE, 0f)).intoArray(out, base + hand);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                float sum = this.predictedPlusSum[hand];
                out[index] = sum != 0 ? this.predictedPlus[index] / sum : uniform;
            }
        }
    }

    @Override
    public void updateRegrets(float[] regrets, int iterationNumber, float[] reachProbs) {
        if (regrets.length != this.actionNumber * this.cardNumber) throw new RuntimeException("length not match");

        // Accumulate the strategy that was just played (still derivable from the previous
        // prediction state) with quadratic weighting before overwriting that state.
        float weight = (float) iterationNumber * iterationNumber;
        float uniform = 1F / this.actionNumber;
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector sum = FloatVector.fromArray(F, this.predictedPlusSum, hand);
                FloatVector played = uniformV.blend(
                        FloatVector.fromArray(F, this.predictedPlus, index).div(sum),
                        sum.compare(VectorOperators.NE, 0f));
                played.fma(
                                FloatVector.fromArray(F, reachProbs, hand).mul(weight),
                                FloatVector.fromArray(F, this.cumStrategy, index))
                        .intoArray(this.cumStrategy, index);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                float sum = this.predictedPlusSum[hand];
                float played = sum != 0 ? this.predictedPlus[index] / sum : uniform;
                this.cumStrategy[index] += played * weight * reachProbs[hand];
            }
        }

        Arrays.fill(this.predictedPlusSum, 0);
        FloatVector zero = FloatVector.zero(F);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector reg = FloatVector.fromArray(F, regrets, index);
                // RM+ accumulator: R = [R + r]+
                FloatVector r =
                        FloatVector.fromArray(F, this.rPlus, index).add(reg).max(zero);
                r.intoArray(this.rPlus, index);
                // Optimistic prediction m = r (the regret just observed): play ∝ [R + m]+
                FloatVector predicted = r.add(reg).max(zero);
                predicted.intoArray(this.predictedPlus, index);
                FloatVector.fromArray(F, this.predictedPlusSum, hand)
                        .add(predicted)
                        .intoArray(this.predictedPlusSum, hand);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                float oneReg = regrets[index];
                this.rPlus[index] = Math.max(0, this.rPlus[index] + oneReg);
                this.predictedPlus[index] = Math.max(0, this.rPlus[index] + oneReg);
                this.predictedPlusSum[hand] += this.predictedPlus[index];
            }
        }
    }
}

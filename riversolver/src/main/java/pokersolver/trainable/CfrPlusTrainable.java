package pokersolver.trainable;

import java.util.Arrays;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * CFR+ (regret-matching+): cumulative regret clipped each step {@code R = [R + r]+}, so the current
 * strategy normalizes R directly, and the average strategy is accumulated with linear (iteration)
 * weighting.
 */
public class CfrPlusTrainable extends RegretMatchingTrainable {

    public CfrPlusTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    @Override
    protected float[] strategyForDump() {
        return getcurrentStrategy();
    }

    @Override
    public float[] getcurrentStrategy() {
        // strategy = sum != 0 ? R+ / sum : uniform. Lanes with sum == 0 divide to Inf/NaN but are
        // blended away.
        float uniform = 1F / this.actionNumber;
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                FloatVector sum = FloatVector.fromArray(F, this.rPlusSum, hand);
                FloatVector normalized =
                        FloatVector.fromArray(F, this.rPlus, base + hand).div(sum);
                uniformV.blend(normalized, sum.compare(VectorOperators.NE, 0f))
                        .intoArray(cachedCurrentStrategy, base + hand);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                cachedCurrentStrategy[index] =
                        this.rPlusSum[hand] != 0 ? this.rPlus[index] / this.rPlusSum[hand] : uniform;
            }
        }
        return cachedCurrentStrategy;
    }

    @Override
    public void updateRegrets(float[] regrets, int iterationNumber, float[] reachProbs) {
        this.regrets = regrets;
        if (regrets.length != this.actionNumber * this.cardNumber) throw new RuntimeException("length not match");

        Arrays.fill(this.rPlusSum, 0);
        Arrays.fill(this.cumRPlusSum, 0);
        // R = [R + r]+ with linearly weighted strategy accumulation (cum += R * t).
        FloatVector zero = FloatVector.zero(F);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector r = FloatVector.fromArray(F, this.rPlus, index)
                        .add(FloatVector.fromArray(F, regrets, index))
                        .max(zero);
                r.intoArray(this.rPlus, index);
                FloatVector.fromArray(F, this.rPlusSum, hand).add(r).intoArray(this.rPlusSum, hand);

                FloatVector cum = FloatVector.fromArray(F, this.cumRPlus, index).add(r.mul((float) iterationNumber));
                cum.intoArray(this.cumRPlus, index);
                FloatVector.fromArray(F, this.cumRPlusSum, hand).add(cum).intoArray(this.cumRPlusSum, hand);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                this.rPlus[index] = Math.max(0, regrets[index] + this.rPlus[index]);
                this.rPlusSum[hand] += this.rPlus[index];

                this.cumRPlus[index] += this.rPlus[index] * iterationNumber;
                this.cumRPlusSum[hand] += this.cumRPlus[index];
            }
        }
    }
}

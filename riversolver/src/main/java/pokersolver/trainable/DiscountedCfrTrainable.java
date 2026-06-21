package pokersolver.trainable;

import java.util.Arrays;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Discounted CFR+ (Brown &amp; Sandholm 2019): cumulative regret discounted by {@code alpha}/{@code
 * beta} depending on sign, and the average strategy accumulated with {@code gamma}/{@code theta}
 * weighting. Converges faster than CFR/CFR+ on poker subgames; the dumped strategy is the average.
 */
public class DiscountedCfrTrainable extends RegretMatchingTrainable {

    float alpha = 1.5f;
    float beta = 0.5f;
    float gamma = 2;
    float theta = 0.9f;

    public DiscountedCfrTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    @Override
    protected float[] strategyForDump() {
        return getAverageStrategy();
    }

    @Override
    public float[] getcurrentStrategy() {
        // strategy = sum != 0 ? [R]+ / sum : uniform. Lanes with sum == 0 divide to Inf/NaN but
        // are blended away.
        float uniform = 1F / this.actionNumber;
        FloatVector zero = FloatVector.zero(F);
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                FloatVector sum = FloatVector.fromArray(F, this.rPlusSum, hand);
                FloatVector normalized = FloatVector.fromArray(F, this.rPlus, base + hand)
                        .max(zero)
                        .div(sum);
                uniformV.blend(normalized, sum.compare(VectorOperators.NE, 0f))
                        .intoArray(cachedCurrentStrategy, base + hand);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                cachedCurrentStrategy[index] =
                        this.rPlusSum[hand] != 0 ? Math.max(this.rPlus[index], 0) / this.rPlusSum[hand] : uniform;
            }
        }
        return cachedCurrentStrategy;
    }

    @Override
    public void updateRegrets(float[] regrets, int iterationNumber, float[] reachProbs) {
        this.regrets = regrets;
        if (regrets.length != this.actionNumber * this.cardNumber) throw new RuntimeException("length not match");

        float alphaCoef = (float) Math.pow((double) iterationNumber, this.alpha);
        alphaCoef = alphaCoef / (1 + alphaCoef);

        Arrays.fill(this.rPlusSum, 0);
        Arrays.fill(this.cumRPlusSum, 0);
        // R = (R + r) scaled by alpha_coef when positive, beta when not (discounting).
        FloatVector zero = FloatVector.zero(F);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector r =
                        FloatVector.fromArray(F, this.rPlus, index).add(FloatVector.fromArray(F, regrets, index));
                VectorMask<Float> positive = r.compare(VectorOperators.GT, 0f);
                r = r.mul(beta).blend(r.mul(alphaCoef), positive);
                r.intoArray(this.rPlus, index);
                FloatVector.fromArray(F, this.rPlusSum, hand).add(r.max(zero)).intoArray(this.rPlusSum, hand);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                float r = this.rPlus[index] + regrets[index];
                r *= r > 0 ? alphaCoef : beta;
                this.rPlus[index] = r;
                this.rPlusSum[hand] += Math.max(0, r);
            }
        }
        float[] currentStrategy = this.getcurrentStrategy();
        float strategyCoef = (float) Math.pow(((float) iterationNumber / (iterationNumber + 1)), gamma);
        // cum = cum * theta + strategy * strategy_coef * reach
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            int base = actionId * this.cardNumber;
            int hand = 0;
            for (; hand <= this.cardNumber - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector cum = FloatVector.fromArray(F, this.cumRPlus, index)
                        .mul(this.theta)
                        .add(FloatVector.fromArray(F, currentStrategy, index)
                                .mul(strategyCoef)
                                .mul(FloatVector.fromArray(F, reachProbs, hand)));
                cum.intoArray(this.cumRPlus, index);
                FloatVector.fromArray(F, this.cumRPlusSum, hand).add(cum).intoArray(this.cumRPlusSum, hand);
            }
            for (; hand < this.cardNumber; hand++) {
                int index = base + hand;
                this.cumRPlus[index] *= this.theta;
                this.cumRPlus[index] += currentStrategy[index] * strategyCoef * reachProbs[hand];
                this.cumRPlusSum[hand] += this.cumRPlus[index];
            }
        }
    }
}

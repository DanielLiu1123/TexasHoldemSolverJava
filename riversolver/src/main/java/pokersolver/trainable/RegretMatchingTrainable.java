package pokersolver.trainable;

import java.util.Arrays;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Shared storage for the regret-matching variants whose average strategy is a weight-accumulated
 * sum (CFR, CFR+, Discounted CFR). They keep the cumulative clipped regret {@code r_plus} and its
 * per-hand sum, plus a strategy accumulator {@code cum_r_plus} and its per-hand sum; the average
 * strategy normalizes the latter. Only the regret-update rule and the current-strategy derivation
 * differ between them, so those stay abstract.
 *
 * <p>Predictive CFR+ uses a different accumulator shape and therefore extends
 * {@link AbstractCfrTrainable} directly rather than this class.
 */
abstract class RegretMatchingTrainable extends AbstractCfrTrainable {

    final float[] rPlus;
    final float[] rPlusSum;
    final float[] cumRPlus;
    final float[] cumRPlusSum;
    float[] regrets;

    protected RegretMatchingTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
        this.rPlus = new float[this.actionNumber * this.cardNumber];
        this.rPlusSum = new float[this.cardNumber];
        this.cumRPlus = new float[this.actionNumber * this.cardNumber];
        this.cumRPlusSum = new float[this.cardNumber];
        this.regrets = new float[this.actionNumber * this.cardNumber];
    }

    @Override
    public float[] getAverageStrategy() {
        float[] retval = new float[this.actionNumber * this.cardNumber];
        if (isAllZeros(this.cumRPlusSum)) {
            Arrays.fill(retval, 1F / this.actionNumber);
        } else {
            for (int actionId = 0; actionId < actionNumber; actionId++) {
                for (int privateId = 0; privateId < this.cardNumber; privateId++) {
                    int index = actionId * this.cardNumber + privateId;
                    if (this.cumRPlusSum[privateId] != 0) {
                        retval[index] = this.cumRPlus[index] / this.cumRPlusSum[privateId];
                    } else {
                        retval[index] = 1F / this.actionNumber;
                    }
                }
            }
        }
        return retval;
    }
}

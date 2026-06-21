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

    final float[] r_plus;
    final float[] r_plus_sum;
    final float[] cum_r_plus;
    final float[] cum_r_plus_sum;
    float[] regrets;

    protected RegretMatchingTrainable(ActionNode action_node, PrivateCards[] privateCards) {
        super(action_node, privateCards);
        this.r_plus = new float[this.action_number * this.card_number];
        this.r_plus_sum = new float[this.card_number];
        this.cum_r_plus = new float[this.action_number * this.card_number];
        this.cum_r_plus_sum = new float[this.card_number];
        this.regrets = new float[this.action_number * this.card_number];
    }

    @Override
    public float[] getAverageStrategy() {
        float[] retval = new float[this.action_number * this.card_number];
        if (isAllZeros(this.cum_r_plus_sum)) {
            Arrays.fill(retval, 1F / this.action_number);
        } else {
            for (int action_id = 0; action_id < action_number; action_id++) {
                for (int private_id = 0; private_id < this.card_number; private_id++) {
                    int index = action_id * this.card_number + private_id;
                    if (this.cum_r_plus_sum[private_id] != 0) {
                        retval[index] = this.cum_r_plus[index] / this.cum_r_plus_sum[private_id];
                    } else {
                        retval[index] = 1F / this.action_number;
                    }
                }
            }
        }
        return retval;
    }
}

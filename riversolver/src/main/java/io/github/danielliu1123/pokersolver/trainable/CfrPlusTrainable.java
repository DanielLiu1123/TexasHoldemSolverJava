package pokersolver.trainable;

import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;
import java.util.Arrays;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;

/**
 * CFR+ (regret-matching+): cumulative regret clipped each step {@code R = [R + r]+}, so the current
 * strategy normalizes R directly, and the average strategy is accumulated with linear (iteration)
 * weighting.
 */
public class CfrPlusTrainable extends RegretMatchingTrainable {

    public CfrPlusTrainable(ActionNode action_node, PrivateCards[] privateCards) {
        super(action_node, privateCards);
    }

    @Override
    protected float[] strategyForDump() {
        return getcurrentStrategy();
    }

    @Override
    public float[] getcurrentStrategy() {
        // strategy = sum != 0 ? R+ / sum : uniform. Lanes with sum == 0 divide to Inf/NaN but are
        // blended away.
        float uniform = 1F / this.action_number;
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                FloatVector sum = FloatVector.fromArray(F, this.r_plus_sum, hand);
                FloatVector normalized =
                        FloatVector.fromArray(F, this.r_plus, base + hand).div(sum);
                uniformV.blend(normalized, sum.compare(VectorOperators.NE, 0f))
                        .intoArray(cachedCurrentStrategy, base + hand);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                cachedCurrentStrategy[index] =
                        this.r_plus_sum[hand] != 0 ? this.r_plus[index] / this.r_plus_sum[hand] : uniform;
            }
        }
        return cachedCurrentStrategy;
    }

    @Override
    public void updateRegrets(float[] regrets, int iteration_number, float[] reach_probs) {
        this.regrets = regrets;
        if (regrets.length != this.action_number * this.card_number) throw new RuntimeException("length not match");

        Arrays.fill(this.r_plus_sum, 0);
        Arrays.fill(this.cum_r_plus_sum, 0);
        // R = [R + r]+ with linearly weighted strategy accumulation (cum += R * t).
        FloatVector zero = FloatVector.zero(F);
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector r = FloatVector.fromArray(F, this.r_plus, index)
                        .add(FloatVector.fromArray(F, regrets, index))
                        .max(zero);
                r.intoArray(this.r_plus, index);
                FloatVector.fromArray(F, this.r_plus_sum, hand).add(r).intoArray(this.r_plus_sum, hand);

                FloatVector cum =
                        FloatVector.fromArray(F, this.cum_r_plus, index).add(r.mul((float) iteration_number));
                cum.intoArray(this.cum_r_plus, index);
                FloatVector.fromArray(F, this.cum_r_plus_sum, hand).add(cum).intoArray(this.cum_r_plus_sum, hand);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                this.r_plus[index] = Math.max(0, regrets[index] + this.r_plus[index]);
                this.r_plus_sum[hand] += this.r_plus[index];

                this.cum_r_plus[index] += this.r_plus[index] * iteration_number;
                this.cum_r_plus_sum[hand] += this.cum_r_plus[index];
            }
        }
    }
}

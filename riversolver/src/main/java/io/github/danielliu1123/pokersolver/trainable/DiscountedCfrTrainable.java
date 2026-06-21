package pokersolver.trainable;

import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;
import java.util.Arrays;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;

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

    public DiscountedCfrTrainable(ActionNode action_node, PrivateCards[] privateCards) {
        super(action_node, privateCards);
    }

    @Override
    protected float[] strategyForDump() {
        return getAverageStrategy();
    }

    @Override
    public float[] getcurrentStrategy() {
        // strategy = sum != 0 ? [R]+ / sum : uniform. Lanes with sum == 0 divide to Inf/NaN but
        // are blended away.
        float uniform = 1F / this.action_number;
        FloatVector zero = FloatVector.zero(F);
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                FloatVector sum = FloatVector.fromArray(F, this.r_plus_sum, hand);
                FloatVector normalized = FloatVector.fromArray(F, this.r_plus, base + hand)
                        .max(zero)
                        .div(sum);
                uniformV.blend(normalized, sum.compare(VectorOperators.NE, 0f))
                        .intoArray(cachedCurrentStrategy, base + hand);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                cachedCurrentStrategy[index] =
                        this.r_plus_sum[hand] != 0 ? Math.max(this.r_plus[index], 0) / this.r_plus_sum[hand] : uniform;
            }
        }
        return cachedCurrentStrategy;
    }

    @Override
    public void updateRegrets(float[] regrets, int iteration_number, float[] reach_probs) {
        this.regrets = regrets;
        if (regrets.length != this.action_number * this.card_number) throw new RuntimeException("length not match");

        float alpha_coef = (float) Math.pow((double) iteration_number, this.alpha);
        alpha_coef = alpha_coef / (1 + alpha_coef);

        Arrays.fill(this.r_plus_sum, 0);
        Arrays.fill(this.cum_r_plus_sum, 0);
        // R = (R + r) scaled by alpha_coef when positive, beta when not (discounting).
        FloatVector zero = FloatVector.zero(F);
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector r =
                        FloatVector.fromArray(F, this.r_plus, index).add(FloatVector.fromArray(F, regrets, index));
                VectorMask<Float> positive = r.compare(VectorOperators.GT, 0f);
                r = r.mul(beta).blend(r.mul(alpha_coef), positive);
                r.intoArray(this.r_plus, index);
                FloatVector.fromArray(F, this.r_plus_sum, hand).add(r.max(zero)).intoArray(this.r_plus_sum, hand);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                float r = this.r_plus[index] + regrets[index];
                r *= r > 0 ? alpha_coef : beta;
                this.r_plus[index] = r;
                this.r_plus_sum[hand] += Math.max(0, r);
            }
        }
        float[] current_strategy = this.getcurrentStrategy();
        float strategy_coef = (float) Math.pow(((float) iteration_number / (iteration_number + 1)), gamma);
        // cum = cum * theta + strategy * strategy_coef * reach
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector cum = FloatVector.fromArray(F, this.cum_r_plus, index)
                        .mul(this.theta)
                        .add(FloatVector.fromArray(F, current_strategy, index)
                                .mul(strategy_coef)
                                .mul(FloatVector.fromArray(F, reach_probs, hand)));
                cum.intoArray(this.cum_r_plus, index);
                FloatVector.fromArray(F, this.cum_r_plus_sum, hand).add(cum).intoArray(this.cum_r_plus_sum, hand);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                this.cum_r_plus[index] *= this.theta;
                this.cum_r_plus[index] += current_strategy[index] * strategy_coef * reach_probs[hand];
                this.cum_r_plus_sum[hand] += this.cum_r_plus[index];
            }
        }
    }
}

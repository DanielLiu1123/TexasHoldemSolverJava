package pokersolver.trainable;

import java.util.Arrays;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Vanilla CFR: cumulative regret {@code R += r} (unclipped), strategy ∝ [R]+, average strategy
 * accumulated with linear (iteration) weighting.
 */
public class CfrTrainable extends RegretMatchingTrainable {

    public CfrTrainable(ActionNode action_node, PrivateCards[] privateCards) {
        super(action_node, privateCards);
    }

    @Override
    protected float[] strategyForDump() {
        return getcurrentStrategy();
    }

    @Override
    public float[] getcurrentStrategy() {
        if (this.r_plus_sum == null) {
            Arrays.fill(cachedCurrentStrategy, 1F / this.action_number);
        } else {
            for (int action_id = 0; action_id < action_number; action_id++) {
                for (int private_id = 0; private_id < this.card_number; private_id++) {
                    int index = action_id * this.card_number + private_id;
                    if (this.r_plus_sum[private_id] != 0) {
                        cachedCurrentStrategy[index] = Math.max(this.r_plus[index], 0) / this.r_plus_sum[private_id];
                    } else {
                        cachedCurrentStrategy[index] = 1F / this.action_number;
                    }
                    if (Float.isNaN(this.r_plus[index])) throw new RuntimeException();
                }
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
        for (int action_id = 0; action_id < action_number; action_id++) {
            for (int private_id = 0; private_id < this.card_number; private_id++) {
                int index = action_id * this.card_number + private_id;
                float one_reg = regrets[index];

                // 更新 R+
                this.r_plus[index] = one_reg + this.r_plus[index];
                this.r_plus_sum[private_id] += Math.max(0, this.r_plus[index]);
            }
        }

        float[] current_strategy = this.getcurrentStrategy();
        for (int action_id = 0; action_id < action_number; action_id++) {
            for (int private_id = 0; private_id < this.card_number; private_id++) {
                int index = action_id * this.card_number + private_id;
                this.cum_r_plus[index] += current_strategy[index] * iteration_number * reach_probs[private_id];
                this.cum_r_plus_sum[private_id] += this.cum_r_plus[index];
            }
        }
    }
}

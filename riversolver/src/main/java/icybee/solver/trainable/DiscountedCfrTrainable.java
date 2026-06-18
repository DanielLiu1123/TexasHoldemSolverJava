package icybee.solver.trainable;

import static icybee.solver.utils.JsonUtil.MAPPER;

import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.GameActions;
import icybee.solver.ranges.PrivateCards;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import tools.jackson.databind.node.ObjectNode;

/**
 * Created by huangxuefeng on 2019/10/12.
 * trainable by discounted cfr
 */
public class DiscountedCfrTrainable extends Trainable {
    static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;

    ActionNode action_node;
    PrivateCards[] privateCards;
    int action_number;
    int card_number;
    float[] r_plus;
    float alpha = 1.5f;
    float beta = 0.5f;
    float gamma = 2;
    float theta = 0.9f;

    float[] r_plus_sum;

    float[] cum_r_plus;
    float[] cum_r_plus_sum;

    float[] regrets;

    float[] cachedCurrentStrategy;

    public DiscountedCfrTrainable(ActionNode action_node, PrivateCards[] privateCards) {
        this.action_node = action_node;
        this.privateCards = privateCards;
        this.action_number = action_node.getChildren().size();
        this.card_number = privateCards.length;

        this.r_plus = new float[this.action_number * this.card_number];
        this.r_plus_sum = new float[this.card_number];

        this.cum_r_plus = new float[this.action_number * this.card_number];
        this.cum_r_plus_sum = new float[this.card_number];
        this.regrets = new float[this.action_number * this.card_number];
        this.cachedCurrentStrategy = new float[this.action_number * this.card_number];
    }

    private boolean isAllZeros(float[] input_array) {
        for (float i : input_array) {
            if (i != 0) return false;
        }
        return true;
    }

    @Override
    public float[] getAverageStrategy() {
        float[] retval = new float[this.action_number * this.card_number];
        if (this.cum_r_plus_sum == null || this.isAllZeros(this.cum_r_plus_sum)) {
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
        // return this.getcurrentStrategy();
        return retval;
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

    @Override
    public ObjectNode dumps(boolean with_state) {
        if (with_state) throw new RuntimeException("state storage not implemented");

        ObjectNode strategy = MAPPER.createObjectNode();
        float[] average_strategy = this.getAverageStrategy();
        List<GameActions> game_actions = action_node.getActions();
        List<String> actions_str = new ArrayList<>();
        for (GameActions one_action : game_actions) actions_str.add(one_action.toString());

        for (int i = 0; i < this.privateCards.length; i++) {
            PrivateCards one_private_card = this.privateCards[i];
            float[] one_strategy = new float[this.action_number];

            for (int j = 0; j < this.action_number; j++) {
                int strategy_index = j * this.privateCards.length + i;
                one_strategy[j] = average_strategy[strategy_index];
            }
            strategy.set(String.format("%s", one_private_card.toString()), MAPPER.valueToTree(one_strategy));
        }

        ObjectNode retjson = MAPPER.createObjectNode();
        retjson.set("actions", MAPPER.valueToTree(actions_str));
        retjson.set("strategy", strategy);
        return retjson;
    }
}

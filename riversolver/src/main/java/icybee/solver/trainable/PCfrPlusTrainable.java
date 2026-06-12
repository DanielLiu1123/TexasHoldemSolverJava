package icybee.solver.trainable;

import static icybee.solver.utils.JsonUtil.MAPPER;

import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.GameActions;
import icybee.solver.ranges.PrivateCards;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/**
 * Predictive CFR+ (PCFR+, Farina/Kroer/Sandholm 2021, "Faster Game Solving via Predictive
 * Blackwell Approachability").
 *
 * <p>Regret-matching+ with an optimistic prediction step: cumulative regrets update exactly like
 * RM+ ({@code R = [R + r]+}), but the strategy for the next iteration is proportional to
 * {@code [R + m]+} where the prediction {@code m} is the most recent instantaneous regret vector.
 * The average strategy uses quadratic (t²) weighting, which the paper pairs with PCFR+.
 */
public class PCfrPlusTrainable extends Trainable {

    final ActionNode action_node;
    final PrivateCards[] privateCards;
    final int action_number;
    final int card_number;

    /** Cumulative clipped regrets R (RM+ accumulator). */
    final float[] r_plus;

    /** Strategy basis [R + m]+ from the latest update; the played strategy normalizes this. */
    final float[] predicted_plus;

    final float[] predicted_plus_sum;

    /** Average strategy accumulator: played strategy × reach × t². */
    final float[] cum_strategy;

    final float[] cachedCurrentStrategy;

    public PCfrPlusTrainable(ActionNode action_node, PrivateCards[] privateCards) {
        this.action_node = action_node;
        this.privateCards = privateCards;
        this.action_number = action_node.getChildren().size();
        this.card_number = privateCards.length;

        this.r_plus = new float[this.action_number * this.card_number];
        this.predicted_plus = new float[this.action_number * this.card_number];
        this.predicted_plus_sum = new float[this.card_number];
        this.cum_strategy = new float[this.action_number * this.card_number];
        this.cachedCurrentStrategy = new float[this.action_number * this.card_number];
    }

    @Override
    public float[] getAverageStrategy() {
        float[] retval = new float[this.action_number * this.card_number];
        for (int private_id = 0; private_id < this.card_number; private_id++) {
            float sum = 0;
            for (int action_id = 0; action_id < action_number; action_id++) {
                sum += this.cum_strategy[action_id * this.card_number + private_id];
            }
            for (int action_id = 0; action_id < action_number; action_id++) {
                int index = action_id * this.card_number + private_id;
                retval[index] = sum != 0 ? this.cum_strategy[index] / sum : 1F / this.action_number;
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
        for (int action_id = 0; action_id < action_number; action_id++) {
            for (int private_id = 0; private_id < this.card_number; private_id++) {
                int index = action_id * this.card_number + private_id;
                float sum = this.predicted_plus_sum[private_id];
                out[index] = sum != 0 ? this.predicted_plus[index] / sum : 1F / this.action_number;
            }
        }
    }

    @Override
    public void updateRegrets(float[] regrets, int iteration_number, float[] reach_probs) {
        if (regrets.length != this.action_number * this.card_number) throw new RuntimeException("length not match");

        // Accumulate the strategy that was just played (still derivable from the previous
        // prediction state) with quadratic weighting before overwriting that state.
        float weight = (float) iteration_number * iteration_number;
        for (int action_id = 0; action_id < action_number; action_id++) {
            for (int private_id = 0; private_id < this.card_number; private_id++) {
                int index = action_id * this.card_number + private_id;
                float sum = this.predicted_plus_sum[private_id];
                float played = sum != 0 ? this.predicted_plus[index] / sum : 1F / this.action_number;
                this.cum_strategy[index] += played * weight * reach_probs[private_id];
            }
        }

        Arrays.fill(this.predicted_plus_sum, 0);
        for (int action_id = 0; action_id < action_number; action_id++) {
            for (int private_id = 0; private_id < this.card_number; private_id++) {
                int index = action_id * this.card_number + private_id;
                float one_reg = regrets[index];

                // RM+ accumulator: R = [R + r]+
                this.r_plus[index] = Math.max(0, this.r_plus[index] + one_reg);
                // Optimistic prediction m = r (the regret just observed): play ∝ [R + m]+
                this.predicted_plus[index] = Math.max(0, this.r_plus[index] + one_reg);
                this.predicted_plus_sum[private_id] += this.predicted_plus[index];
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
                one_strategy[j] = average_strategy[j * this.privateCards.length + i];
            }
            strategy.set(String.format("%s", one_private_card.toString()), MAPPER.valueToTree(one_strategy));
        }

        ObjectNode retjson = MAPPER.createObjectNode();
        retjson.set("actions", MAPPER.valueToTree(actions_str));
        retjson.set("strategy", strategy);
        return retjson;
    }
}

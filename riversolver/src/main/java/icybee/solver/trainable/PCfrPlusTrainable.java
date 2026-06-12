package icybee.solver.trainable;

import static icybee.solver.utils.JsonUtil.MAPPER;

import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.GameActions;
import icybee.solver.ranges.PrivateCards;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
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

    static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;

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
        float uniform = 1F / this.action_number;
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                FloatVector sum = FloatVector.fromArray(F, this.predicted_plus_sum, hand);
                FloatVector normalized = FloatVector.fromArray(F, this.predicted_plus, base + hand)
                        .div(sum);
                uniformV.blend(normalized, sum.compare(VectorOperators.NE, 0f)).intoArray(out, base + hand);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                float sum = this.predicted_plus_sum[hand];
                out[index] = sum != 0 ? this.predicted_plus[index] / sum : uniform;
            }
        }
    }

    @Override
    public void updateRegrets(float[] regrets, int iteration_number, float[] reach_probs) {
        if (regrets.length != this.action_number * this.card_number) throw new RuntimeException("length not match");

        // Accumulate the strategy that was just played (still derivable from the previous
        // prediction state) with quadratic weighting before overwriting that state.
        float weight = (float) iteration_number * iteration_number;
        float uniform = 1F / this.action_number;
        FloatVector uniformV = FloatVector.broadcast(F, uniform);
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector sum = FloatVector.fromArray(F, this.predicted_plus_sum, hand);
                FloatVector played = uniformV.blend(
                        FloatVector.fromArray(F, this.predicted_plus, index).div(sum),
                        sum.compare(VectorOperators.NE, 0f));
                played.fma(
                                FloatVector.fromArray(F, reach_probs, hand).mul(weight),
                                FloatVector.fromArray(F, this.cum_strategy, index))
                        .intoArray(this.cum_strategy, index);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                float sum = this.predicted_plus_sum[hand];
                float played = sum != 0 ? this.predicted_plus[index] / sum : uniform;
                this.cum_strategy[index] += played * weight * reach_probs[hand];
            }
        }

        Arrays.fill(this.predicted_plus_sum, 0);
        FloatVector zero = FloatVector.zero(F);
        for (int action_id = 0; action_id < action_number; action_id++) {
            int base = action_id * this.card_number;
            int hand = 0;
            for (; hand <= this.card_number - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector reg = FloatVector.fromArray(F, regrets, index);
                // RM+ accumulator: R = [R + r]+
                FloatVector r =
                        FloatVector.fromArray(F, this.r_plus, index).add(reg).max(zero);
                r.intoArray(this.r_plus, index);
                // Optimistic prediction m = r (the regret just observed): play ∝ [R + m]+
                FloatVector predicted = r.add(reg).max(zero);
                predicted.intoArray(this.predicted_plus, index);
                FloatVector.fromArray(F, this.predicted_plus_sum, hand)
                        .add(predicted)
                        .intoArray(this.predicted_plus_sum, hand);
            }
            for (; hand < this.card_number; hand++) {
                int index = base + hand;
                float one_reg = regrets[index];
                this.r_plus[index] = Math.max(0, this.r_plus[index] + one_reg);
                this.predicted_plus[index] = Math.max(0, this.r_plus[index] + one_reg);
                this.predicted_plus_sum[hand] += this.predicted_plus[index];
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

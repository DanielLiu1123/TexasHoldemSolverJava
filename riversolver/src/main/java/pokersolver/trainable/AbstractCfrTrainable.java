package pokersolver.trainable;

import static pokersolver.utils.JsonUtil.MAPPER;

import java.util.ArrayList;
import java.util.List;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.GameActions;
import pokersolver.ranges.PrivateCards;
import tools.jackson.databind.node.ObjectNode;

/**
 * Shared skeleton for the CFR-family {@link Trainable}s.
 *
 * <p>Every variant stores one mixed strategy per (action, hand) cell, laid out as a flat
 * {@code action_number * card_number} array (action-major), and serializes that strategy to JSON
 * the same way. This base owns that common shape — the per-node dimensions, the SIMD species, the
 * uniform-strategy fallback test, and {@link #dumps} — leaving each subclass only its regret
 * accumulator(s), its strategy derivation, and its regret-update rule.
 */
public abstract class AbstractCfrTrainable extends Trainable {

    static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;

    final ActionNode action_node;
    final PrivateCards[] privateCards;
    final int action_number;
    final int card_number;
    final float[] cachedCurrentStrategy;

    protected AbstractCfrTrainable(ActionNode action_node, PrivateCards[] privateCards) {
        this.action_node = action_node;
        this.privateCards = privateCards;
        this.action_number = action_node.getChildren().size();
        this.card_number = privateCards.length;
        this.cachedCurrentStrategy = new float[this.action_number * this.card_number];
    }

    static boolean isAllZeros(float[] input_array) {
        for (float v : input_array) {
            if (v != 0) return false;
        }
        return true;
    }

    /**
     * The strategy to serialize in {@link #dumps}: the converged answer for this variant. CFR+ and
     * vanilla CFR expose the current strategy; the discounted/predictive variants expose the
     * weighted average accumulator.
     */
    protected abstract float[] strategyForDump();

    @Override
    public final ObjectNode dumps(boolean with_state) {
        if (with_state) throw new RuntimeException("state storage not implemented");

        float[] strategy = strategyForDump();
        List<String> actions_str = new ArrayList<>();
        for (GameActions one_action : action_node.getActions()) actions_str.add(one_action.toString());

        ObjectNode strategy_json = MAPPER.createObjectNode();
        for (int i = 0; i < this.privateCards.length; i++) {
            float[] one_strategy = new float[this.action_number];
            for (int j = 0; j < this.action_number; j++) {
                one_strategy[j] = strategy[j * this.privateCards.length + i];
            }
            strategy_json.set(this.privateCards[i].toString(), MAPPER.valueToTree(one_strategy));
        }

        ObjectNode retjson = MAPPER.createObjectNode();
        retjson.set("actions", MAPPER.valueToTree(actions_str));
        retjson.set("strategy", strategy_json);
        return retjson;
    }
}

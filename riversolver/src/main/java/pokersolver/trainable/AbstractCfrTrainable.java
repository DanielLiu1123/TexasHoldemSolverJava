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

    final ActionNode actionNode;
    final PrivateCards[] privateCards;
    final int actionNumber;
    final int cardNumber;
    final float[] cachedCurrentStrategy;

    protected AbstractCfrTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        this.actionNode = actionNode;
        this.privateCards = privateCards;
        this.actionNumber = actionNode.getChildren().size();
        this.cardNumber = privateCards.length;
        this.cachedCurrentStrategy = new float[this.actionNumber * this.cardNumber];
    }

    static boolean isAllZeros(float[] inputArray) {
        for (float v : inputArray) {
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
    public final ObjectNode dumps(boolean withState) {
        if (withState) throw new RuntimeException("state storage not implemented");

        float[] strategy = strategyForDump();
        List<String> actionsStr = new ArrayList<>();
        for (GameActions oneAction : actionNode.getActions()) actionsStr.add(oneAction.toString());

        ObjectNode strategyJson = MAPPER.createObjectNode();
        for (int i = 0; i < this.privateCards.length; i++) {
            float[] oneStrategy = new float[this.actionNumber];
            for (int j = 0; j < this.actionNumber; j++) {
                oneStrategy[j] = strategy[j * this.privateCards.length + i];
            }
            strategyJson.set(this.privateCards[i].toString(), MAPPER.valueToTree(oneStrategy));
        }

        ObjectNode retjson = MAPPER.createObjectNode();
        retjson.set("actions", MAPPER.valueToTree(actionsStr));
        retjson.set("strategy", strategyJson);
        return retjson;
    }
}

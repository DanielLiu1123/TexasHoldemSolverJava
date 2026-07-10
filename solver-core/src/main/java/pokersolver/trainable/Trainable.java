package pokersolver.trainable;

import tools.jackson.databind.node.ObjectNode;

/**
 * The strategy stored at one {@link pokersolver.nodes.ActionNode}, and the rule that updates it.
 *
 * <p>Every strategy is a flat, action-major {@code float[]} of {@code actionCount * handCount}: cell
 * {@code a * handCount + h} is the probability of taking action {@code a} while holding hand {@code
 * h}. The layout puts each action's probabilities for all hands contiguously, which is what lets the
 * regret update run as a SIMD sweep down one action at a time.
 *
 * <p>A solver calls {@link #update} once per iteration per node it visits with the acting player as
 * the traversal's target, then reads {@link #currentStrategy()} on the next pass. {@link
 * #averageStrategy()} is the answer — the thing that converges to an equilibrium — and is what
 * {@link #dumps()} serializes.
 */
public interface Trainable {

    /**
     * The strategy this node plays on the next iteration, derived from the accumulated regrets.
     *
     * <p>The returned array is the trainable's own buffer, overwritten by the next call. Callers
     * must not retain or mutate it.
     */
    float[] currentStrategy();

    /**
     * The reach-weighted average of every strategy played so far — the solution CFR converges to.
     *
     * <p>Freshly allocated on each call.
     */
    float[] averageStrategy();

    /**
     * Folds one iteration's counterfactual regrets into the accumulators.
     *
     * @param instantRegrets this iteration's regret per (action, hand), same layout as the strategy
     * @param iteration the 1-based iteration number {@code t}
     * @param reachProbs the acting player's reach probability per hand
     */
    void update(float[] instantRegrets, int iteration, float[] reachProbs);

    /** This node's converged strategy as {@code {"actions": [...], "strategy": {hand: [...]}}}. */
    ObjectNode dumps();
}

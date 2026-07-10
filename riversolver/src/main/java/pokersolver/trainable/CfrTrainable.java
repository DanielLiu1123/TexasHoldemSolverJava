package pokersolver.trainable;

import jdk.incubator.vector.FloatVector;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Vanilla CFR (Zinkevich et al. 2007) with linear averaging: cumulative regret {@code R += r},
 * unclipped, and a strategy proportional to {@code [R]⁺}.
 *
 * <p>The current strategy of vanilla CFR does not converge — it cycles around the equilibrium. Only
 * the average does, which is why {@link #averageStrategy()} is the answer and this variant's
 * exploitability falls slowest of the four.
 */
public final class CfrTrainable extends RegretMatchingTrainable {

    public CfrTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    @Override
    void accumulateRegrets(float[] instantRegrets, int iteration) {
        int i = 0;
        for (; i <= regrets.length - F.length(); i += F.length()) {
            FloatVector.fromArray(F, regrets, i)
                    .add(FloatVector.fromArray(F, instantRegrets, i))
                    .intoArray(regrets, i);
        }
        for (; i < regrets.length; i++) regrets[i] += instantRegrets[i];
    }

    @Override
    float strategyWeight(int iteration) {
        return iteration;
    }
}

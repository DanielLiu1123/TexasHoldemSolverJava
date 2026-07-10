package pokersolver.trainable;

import jdk.incubator.vector.FloatVector;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * CFR+ (Tammelin 2014): regret-matching⁺, whose accumulator is clipped at zero each step — {@code R
 * = [R + r]⁺} — so an action that fell out of favour can come back as soon as it starts earning
 * regret again, instead of first climbing out of a deep negative hole.
 *
 * <p>Paired, as the paper prescribes, with linear averaging: iteration {@code t}'s strategy enters
 * the average with weight {@code t}.
 */
public final class CfrPlusTrainable extends RegretMatchingTrainable {

    public CfrPlusTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    @Override
    void accumulateRegrets(float[] instantRegrets, int iteration) {
        FloatVector zero = FloatVector.zero(F);
        int i = 0;
        for (; i <= regrets.length - F.length(); i += F.length()) {
            FloatVector.fromArray(F, regrets, i)
                    .add(FloatVector.fromArray(F, instantRegrets, i))
                    .max(zero)
                    .intoArray(regrets, i);
        }
        for (; i < regrets.length; i++) regrets[i] = Math.max(0, regrets[i] + instantRegrets[i]);
    }

    @Override
    float strategyWeight(int iteration) {
        return iteration;
    }
}

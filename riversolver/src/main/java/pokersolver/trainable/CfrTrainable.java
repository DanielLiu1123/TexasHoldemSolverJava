package pokersolver.trainable;

import java.util.Arrays;
import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/**
 * Vanilla CFR: cumulative regret {@code R += r} (unclipped), strategy ∝ [R]+, average strategy
 * accumulated with linear (iteration) weighting.
 */
public class CfrTrainable extends RegretMatchingTrainable {

    public CfrTrainable(ActionNode actionNode, PrivateCards[] privateCards) {
        super(actionNode, privateCards);
    }

    @Override
    protected float[] strategyForDump() {
        return getcurrentStrategy();
    }

    @Override
    public float[] getcurrentStrategy() {
        if (this.rPlusSum == null) {
            Arrays.fill(cachedCurrentStrategy, 1F / this.actionNumber);
        } else {
            for (int actionId = 0; actionId < actionNumber; actionId++) {
                for (int privateId = 0; privateId < this.cardNumber; privateId++) {
                    int index = actionId * this.cardNumber + privateId;
                    if (this.rPlusSum[privateId] != 0) {
                        cachedCurrentStrategy[index] = Math.max(this.rPlus[index], 0) / this.rPlusSum[privateId];
                    } else {
                        cachedCurrentStrategy[index] = 1F / this.actionNumber;
                    }
                    if (Float.isNaN(this.rPlus[index])) throw new RuntimeException();
                }
            }
        }
        return cachedCurrentStrategy;
    }

    @Override
    public void updateRegrets(float[] regrets, int iterationNumber, float[] reachProbs) {
        this.regrets = regrets;
        if (regrets.length != this.actionNumber * this.cardNumber) throw new RuntimeException("length not match");

        Arrays.fill(this.rPlusSum, 0);
        Arrays.fill(this.cumRPlusSum, 0);
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            for (int privateId = 0; privateId < this.cardNumber; privateId++) {
                int index = actionId * this.cardNumber + privateId;
                float oneReg = regrets[index];

                // 更新 R+
                this.rPlus[index] = oneReg + this.rPlus[index];
                this.rPlusSum[privateId] += Math.max(0, this.rPlus[index]);
            }
        }

        float[] currentStrategy = this.getcurrentStrategy();
        for (int actionId = 0; actionId < actionNumber; actionId++) {
            for (int privateId = 0; privateId < this.cardNumber; privateId++) {
                int index = actionId * this.cardNumber + privateId;
                this.cumRPlus[index] += currentStrategy[index] * iterationNumber * reachProbs[privateId];
                this.cumRPlusSum[privateId] += this.cumRPlus[index];
            }
        }
    }
}

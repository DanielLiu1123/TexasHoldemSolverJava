package pokersolver.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Scalar vs {@code jdk.incubator.vector} SIMD variants of the loop shapes that dominate the CFR
 * walk (see the stack profile of RiverSolveBenchmark): the discounted-CFR regret update, the
 * strategy normalization, and the payoff multiply-accumulate from actionUtility. Decides whether
 * wiring the incubator module into the solver core is worth it — C2 already auto-vectorizes some of
 * these shapes.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings("NullAway.Init") // JMH state fields are initialized in @Setup
public class RegretMatchingBenchmark {

    static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;

    /** C(47,2) river hands × 3 actions, the realistic trainable size. */
    static final int HANDS = 1081;

    static final int ACTIONS = 3;

    float[] rPlus;
    float[] regrets;
    float[] rPlusSum;
    float[] strategy;
    float[] payoffs;
    float[] actionUtility;
    float alphaCoef = 0.7f;
    float beta = 0.5f;

    @Setup(Level.Iteration)
    public void setup() {
        Random random = new Random(7);
        rPlus = new float[ACTIONS * HANDS];
        regrets = new float[ACTIONS * HANDS];
        strategy = new float[ACTIONS * HANDS];
        rPlusSum = new float[HANDS];
        payoffs = new float[HANDS];
        actionUtility = new float[HANDS];
        for (int i = 0; i < rPlus.length; i++) {
            rPlus[i] = random.nextFloat() - 0.5f;
            regrets[i] = random.nextFloat() - 0.5f;
        }
        for (int i = 0; i < HANDS; i++) {
            rPlusSum[i] = random.nextFloat() + 0.1f;
            actionUtility[i] = random.nextFloat();
        }
    }

    // --- discounted CFR regret update: r = (r + reg) * (r + reg > 0 ? alpha : beta) ---

    @Benchmark
    public float[] dcfrUpdateScalar() {
        java.util.Arrays.fill(rPlusSum, 0);
        for (int action = 0; action < ACTIONS; action++) {
            int base = action * HANDS;
            for (int hand = 0; hand < HANDS; hand++) {
                int index = base + hand;
                float r = rPlus[index] + regrets[index];
                r *= r > 0 ? alphaCoef : beta;
                rPlus[index] = r;
                rPlusSum[hand] += Math.max(0, r);
            }
        }
        return rPlusSum;
    }

    @Benchmark
    public float[] dcfrUpdateVector() {
        java.util.Arrays.fill(rPlusSum, 0);
        FloatVector zero = FloatVector.zero(F);
        for (int action = 0; action < ACTIONS; action++) {
            int base = action * HANDS;
            int hand = 0;
            for (; hand <= HANDS - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector r = FloatVector.fromArray(F, rPlus, index).add(FloatVector.fromArray(F, regrets, index));
                VectorMask<Float> positive = r.compare(VectorOperators.GT, 0f);
                r = r.mul(beta).blend(r.mul(alphaCoef), positive);
                r.intoArray(rPlus, index);
                FloatVector sum = FloatVector.fromArray(F, rPlusSum, hand);
                sum.add(r.max(zero)).intoArray(rPlusSum, hand);
            }
            for (; hand < HANDS; hand++) {
                int index = base + hand;
                float r = rPlus[index] + regrets[index];
                r *= r > 0 ? alphaCoef : beta;
                rPlus[index] = r;
                rPlusSum[hand] += Math.max(0, r);
            }
        }
        return rPlusSum;
    }

    // --- strategy normalization: s = sum != 0 ? max(r, 0) / sum : 1/actions ---

    @Benchmark
    public float[] strategyScalar() {
        for (int action = 0; action < ACTIONS; action++) {
            int base = action * HANDS;
            for (int hand = 0; hand < HANDS; hand++) {
                int index = base + hand;
                strategy[index] = rPlusSum[hand] != 0 ? Math.max(rPlus[index], 0) / rPlusSum[hand] : 1f / ACTIONS;
            }
        }
        return strategy;
    }

    @Benchmark
    public float[] strategyVector() {
        FloatVector zero = FloatVector.zero(F);
        FloatVector uniform = FloatVector.broadcast(F, 1f / ACTIONS);
        for (int action = 0; action < ACTIONS; action++) {
            int base = action * HANDS;
            int hand = 0;
            for (; hand <= HANDS - F.length(); hand += F.length()) {
                int index = base + hand;
                FloatVector sum = FloatVector.fromArray(F, rPlusSum, hand);
                VectorMask<Float> nonZero = sum.compare(VectorOperators.NE, 0f);
                FloatVector normalized =
                        FloatVector.fromArray(F, rPlus, index).max(zero).div(sum);
                uniform.blend(normalized, nonZero).intoArray(strategy, index);
            }
            for (; hand < HANDS; hand++) {
                int index = base + hand;
                strategy[index] = rPlusSum[hand] != 0 ? Math.max(rPlus[index], 0) / rPlusSum[hand] : 1f / ACTIONS;
            }
        }
        return strategy;
    }

    // --- actionUtility payoff accumulation: payoffs += strategy_lane * utility ---

    @Benchmark
    public float[] payoffScalar() {
        for (int action = 0; action < ACTIONS; action++) {
            int base = action * HANDS;
            for (int hand = 0; hand < HANDS; hand++) {
                payoffs[hand] += strategy[base + hand] * actionUtility[hand];
            }
        }
        return payoffs;
    }

    @Benchmark
    public float[] payoffVector() {
        for (int action = 0; action < ACTIONS; action++) {
            int base = action * HANDS;
            int hand = 0;
            for (; hand <= HANDS - F.length(); hand += F.length()) {
                FloatVector.fromArray(F, strategy, base + hand)
                        .fma(FloatVector.fromArray(F, actionUtility, hand), FloatVector.fromArray(F, payoffs, hand))
                        .intoArray(payoffs, hand);
            }
            for (; hand < HANDS; hand++) {
                payoffs[hand] += strategy[base + hand] * actionUtility[hand];
            }
        }
        return payoffs;
    }
}

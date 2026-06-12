package icybee.solver.utils;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * Elementwise float-array kernels for the CFR hot path (reach-probability scaling, payoff
 * accumulation, regret computation). C2 does not reliably auto-vectorize these shapes — the
 * explicit {@code jdk.incubator.vector} versions measure 2-5x faster (see
 * RegretMatchingBenchmark) — so the whole build runs with {@code --add-modules
 * jdk.incubator.vector}.
 */
public final class SimdOps {

    static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;

    private SimdOps() {}

    /** {@code dst[i] = a[aOffset + i] * b[i]} */
    public static void mul(float[] a, int aOffset, float[] b, float[] dst, int length) {
        int i = 0;
        for (; i <= length - F.length(); i += F.length()) {
            FloatVector.fromArray(F, a, aOffset + i)
                    .mul(FloatVector.fromArray(F, b, i))
                    .intoArray(dst, i);
        }
        for (; i < length; i++) dst[i] = a[aOffset + i] * b[i];
    }

    /** {@code dst[i] += a[aOffset + i] * b[i]} */
    public static void fma(float[] a, int aOffset, float[] b, float[] dst, int length) {
        int i = 0;
        for (; i <= length - F.length(); i += F.length()) {
            FloatVector.fromArray(F, a, aOffset + i)
                    .fma(FloatVector.fromArray(F, b, i), FloatVector.fromArray(F, dst, i))
                    .intoArray(dst, i);
        }
        for (; i < length; i++) dst[i] += a[aOffset + i] * b[i];
    }

    /** {@code dst[i] += src[i]} */
    public static void add(float[] src, float[] dst, int length) {
        int i = 0;
        for (; i <= length - F.length(); i += F.length()) {
            FloatVector.fromArray(F, src, i)
                    .add(FloatVector.fromArray(F, dst, i))
                    .intoArray(dst, i);
        }
        for (; i < length; i++) dst[i] += src[i];
    }

    /** {@code dst[dstOffset + i] = a[i] - b[i]} */
    public static void sub(float[] a, float[] b, float[] dst, int dstOffset, int length) {
        int i = 0;
        for (; i <= length - F.length(); i += F.length()) {
            FloatVector.fromArray(F, a, i).sub(FloatVector.fromArray(F, b, i)).intoArray(dst, dstOffset + i);
        }
        for (; i < length; i++) dst[dstOffset + i] = a[i] - b[i];
    }
}

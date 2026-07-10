package pokersolver.solver;

import pokersolver.trainable.CfrPlusTrainable;
import pokersolver.trainable.CfrTrainable;
import pokersolver.trainable.DiscountedCfrTrainable;
import pokersolver.trainable.PCfrPlusTrainable;
import pokersolver.trainable.PDCfrPlusTrainable;
import pokersolver.trainable.PDCfrTrainable;
import pokersolver.trainable.TrainableFactory;

/**
 * The CFR variant used to update regrets and strategies at each action node.
 *
 * <p>Exploitability (percentage of the pot, lower is better) after 200 single-threaded iterations,
 * measured by {@code AlgorithmBakeoff}:
 *
 * <pre>
 *                    sd-river  sd-river  holdem   shortdeck
 *                    (narrow)  (wide)    river    turn
 *   cfr              0.052     0.052     0.220    3.003
 *   cfr_plus         0.0059    0.0097    0.0100   0.288
 *   pcfr_plus        0.0031    0.0103    0.0251   0.287
 *   pdcfr_plus       0.0032    0.0280    0.0213   0.227
 *   pdcfr            0.0018    0.0095    0.0174   0.201
 *   discounted_cfr   0.0009    0.0014    0.0051   0.096   &lt;- default
 * </pre>
 *
 * <p>{@link #DISCOUNTED_CFR} wins every scenario, so it is the default. The optimistic variants lead
 * on matrix games and lose here, which their own papers predict: a poker subgame's regret sequence
 * is not smooth enough for the prediction to pay for itself, and PCFR+'s clipped accumulator throws
 * away the signed negative regret that DCFR's β discount exploits. {@link #PDCFR} beats {@link
 * #PDCFR_PLUS} for exactly that reason — it keeps the sign.
 *
 * <p>Re-measure with {@code AlgorithmBakeoff} before changing the default.
 */
public enum Algorithm {

    /** Vanilla CFR with linear averaging (Zinkevich et al. 2007). The textbook baseline. */
    CFR("cfr", CfrTrainable::new),

    /** Regret-matching⁺ with linear averaging (Tammelin 2014). */
    CFR_PLUS("cfr_plus", CfrPlusTrainable::new),

    /** RM⁺ with an optimistic prediction step and quadratic averaging (Farina et al. 2021). */
    PCFR_PLUS("pcfr_plus", PCfrPlusTrainable::new),

    /** RM⁺ discounting plus optimistic prediction (Xu et al. 2024). */
    PDCFR_PLUS("pdcfr_plus", PDCfrPlusTrainable::new),

    /** Signed sign-dependent discounting plus optimistic prediction (Xu et al. 2024). */
    PDCFR("pdcfr", PDCfrTrainable::new),

    /** Sign-dependent regret discounting with polynomial averaging (Brown &amp; Sandholm 2019). The default. */
    DISCOUNTED_CFR("discounted_cfr", DiscountedCfrTrainable::new);

    private final String id;
    private final TrainableFactory trainableFactory;

    Algorithm(String id, TrainableFactory trainableFactory) {
        this.id = id;
        this.trainableFactory = trainableFactory;
    }

    public String id() {
        return id;
    }

    public TrainableFactory trainableFactory() {
        return trainableFactory;
    }

    /** Parses the external identifier used by the CLI and the HTTP API. */
    public static Algorithm fromId(String id) {
        for (Algorithm algorithm : values()) {
            if (algorithm.id.equals(id)) return algorithm;
        }
        throw new IllegalArgumentException(String.format("algorithm not found: %s", id));
    }
}

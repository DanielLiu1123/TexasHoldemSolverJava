package icybee.solver.solver;

import icybee.solver.trainable.CfrPlusTrainable;
import icybee.solver.trainable.CfrTrainable;
import icybee.solver.trainable.DiscountedCfrTrainable;
import icybee.solver.trainable.PCfrPlusTrainable;
import icybee.solver.trainable.TrainableFactory;

/** The CFR variant used to update regrets and strategies at each action node. */
public enum Algorithm {
    CFR("cfr", CfrTrainable::new),
    CFR_PLUS("cfr_plus", CfrPlusTrainable::new),
    DISCOUNTED_CFR("discounted_cfr", DiscountedCfrTrainable::new),
    PCFR_PLUS("pcfr_plus", PCfrPlusTrainable::new);

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

    /** Parses the external identifier used by the CLI and API ("cfr", "cfr_plus", "discounted_cfr"). */
    public static Algorithm fromId(String id) {
        for (Algorithm algorithm : values()) {
            if (algorithm.id.equals(id)) return algorithm;
        }
        throw new IllegalArgumentException(String.format("algorithm not found: %s", id));
    }
}

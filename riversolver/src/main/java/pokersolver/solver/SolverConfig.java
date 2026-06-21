package pokersolver.solver;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pokersolver.Deck;
import pokersolver.GameTree;
import pokersolver.compairer.Compairer;
import pokersolver.ranges.PrivateCards;
import pokersolver.trainable.DiscountedCfrTrainable;
import pokersolver.trainable.TrainableFactory;

/**
 * Common configuration shared by all CFR solvers.
 *
 * @param stopExploitability stop training early once exploitability (in percent of the pot) drops
 *     below this value; {@code 0} disables early stopping
 */
public record SolverConfig(
        GameTree tree,
        PrivateCards[] range1,
        PrivateCards[] range2,
        int[] initialBoard,
        Compairer compairer,
        Deck deck,
        int iterationNumber,
        boolean debug,
        int printInterval,
        @Nullable String logfile,
        TrainableFactory trainerFactory,
        MonteCarloAlg monteCarloAlg,
        double stopExploitability,
        TrainingProgressListener progressListener) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable GameTree tree;
        private PrivateCards @Nullable [] range1;
        private PrivateCards @Nullable [] range2;
        private int @Nullable [] initialBoard;
        private @Nullable Compairer compairer;
        private @Nullable Deck deck;
        private int iterationNumber = 100;
        private boolean debug = false;
        private int printInterval = 10;
        private @Nullable String logfile;
        private TrainableFactory trainerFactory = DiscountedCfrTrainable::new;
        private MonteCarloAlg monteCarloAlg = MonteCarloAlg.NONE;
        private double stopExploitability = 0;
        private TrainingProgressListener progressListener = TrainingProgressListener.NONE;

        private Builder() {}

        public Builder tree(GameTree tree) {
            this.tree = tree;
            return this;
        }

        public Builder range1(PrivateCards[] range1) {
            this.range1 = range1;
            return this;
        }

        public Builder range2(PrivateCards[] range2) {
            this.range2 = range2;
            return this;
        }

        public Builder initialBoard(int[] initialBoard) {
            this.initialBoard = initialBoard;
            return this;
        }

        public Builder compairer(Compairer compairer) {
            this.compairer = compairer;
            return this;
        }

        public Builder deck(Deck deck) {
            this.deck = deck;
            return this;
        }

        public Builder iterationNumber(int iterationNumber) {
            this.iterationNumber = iterationNumber;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder printInterval(int printInterval) {
            this.printInterval = printInterval;
            return this;
        }

        public Builder logfile(@Nullable String logfile) {
            this.logfile = logfile;
            return this;
        }

        public Builder trainerFactory(TrainableFactory trainerFactory) {
            this.trainerFactory = trainerFactory;
            return this;
        }

        public Builder algorithm(Algorithm algorithm) {
            this.trainerFactory = algorithm.trainableFactory();
            return this;
        }

        public Builder monteCarloAlg(MonteCarloAlg monteCarloAlg) {
            this.monteCarloAlg = monteCarloAlg;
            return this;
        }

        public Builder stopExploitability(double stopExploitability) {
            this.stopExploitability = stopExploitability;
            return this;
        }

        public Builder progressListener(TrainingProgressListener progressListener) {
            this.progressListener = progressListener;
            return this;
        }

        public SolverConfig build() {
            return new SolverConfig(
                    Objects.requireNonNull(tree, "tree"),
                    Objects.requireNonNull(range1, "range1"),
                    Objects.requireNonNull(range2, "range2"),
                    Objects.requireNonNull(initialBoard, "initialBoard"),
                    Objects.requireNonNull(compairer, "compairer"),
                    Objects.requireNonNull(deck, "deck"),
                    iterationNumber,
                    debug,
                    printInterval,
                    logfile,
                    trainerFactory,
                    monteCarloAlg,
                    stopExploitability,
                    progressListener);
        }
    }
}

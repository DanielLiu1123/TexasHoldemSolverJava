package pokersolver.solver;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pokersolver.GameTree;
import pokersolver.ranges.PrivateCards;
import pokersolver.trainable.TrainableFactory;

/**
 * The scenario and the knobs, shared by every CFR solver.
 *
 * @param tree the post-flop game tree to solve
 * @param range1 the in-position player's range
 * @param range2 the out-of-position player's range
 * @param initialBoard the community cards already dealt
 * @param iterationNumber the iteration ceiling
 * @param printInterval how often to evaluate exploitability, in iterations
 * @param logfile where to append a JSON line per evaluation, or null
 * @param trainerFactory the CFR variant, per {@link Algorithm}
 * @param monteCarloAlg whether chance nodes deal every card or one sampled card
 * @param stopExploitability stop once exploitability (as a percentage of the pot) drops below this;
 *     {@code 0} disables early stopping
 * @param progressListener notified at each exploitability evaluation
 */
public record SolverConfig(
        GameTree tree,
        PrivateCards[] range1,
        PrivateCards[] range2,
        int[] initialBoard,
        int iterationNumber,
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
        private int iterationNumber = 100;
        private int printInterval = 10;
        private @Nullable String logfile;
        private TrainableFactory trainerFactory = Algorithm.DISCOUNTED_CFR.trainableFactory();
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

        public Builder iterationNumber(int iterationNumber) {
            this.iterationNumber = iterationNumber;
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

        public Builder algorithm(Algorithm algorithm) {
            this.trainerFactory = algorithm.trainableFactory();
            return this;
        }

        public Builder trainerFactory(TrainableFactory trainerFactory) {
            this.trainerFactory = trainerFactory;
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
                    iterationNumber,
                    printInterval,
                    logfile,
                    trainerFactory,
                    monteCarloAlg,
                    stopExploitability,
                    progressListener);
        }
    }
}

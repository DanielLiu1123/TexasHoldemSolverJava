package icybee.solver.solver;

import icybee.solver.Deck;
import icybee.solver.GameTree;
import icybee.solver.compairer.Compairer;
import icybee.solver.ranges.PrivateCards;
import icybee.solver.trainable.TrainableFactory;
import org.jspecify.annotations.Nullable;

/** Common configuration shared by all CFR solvers. */
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
        MonteCarloAlg monteCarolAlg) {}

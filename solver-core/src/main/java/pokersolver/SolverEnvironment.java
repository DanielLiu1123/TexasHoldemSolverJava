package pokersolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import pokersolver.solver.GameTreeBuildingSettings;

/** Static factories for the solver's game tree. */
public final class SolverEnvironment {

    private SolverEnvironment() {}

    /** Loads a tree from a JSON description produced by the upstream tree builder. */
    public static GameTree gameTreeFromJson(String jsonPath) {
        try {
            return new GameTree(jsonPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Builds a tree from the betting rules. */
    public static GameTree gameTreeFromParams(
            float oopCommit,
            float ipCommit,
            int currentRound,
            int raiseLimit,
            float smallBlind,
            float bigBlind,
            float stack,
            GameTreeBuildingSettings buildingSettings) {
        return new GameTree(
                oopCommit, ipCommit, currentRound, raiseLimit, smallBlind, bigBlind, stack, buildingSettings);
    }
}

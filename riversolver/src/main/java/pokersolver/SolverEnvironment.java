package pokersolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import pokersolver.solver.GameTreeBuildingSettings;

/** Static factories assembling the solver's building blocks — deck and game tree — from configuration. */
public final class SolverEnvironment {

    private SolverEnvironment() {}

    public static Deck deckFromConfig(Config config) {
        return new Deck(config.ranks(), config.suits());
    }

    public static GameTree gameTreeFromConfig(Config config, Deck deck) {
        return gameTreeFromJson(Objects.requireNonNull(config.treeBuilderJson(), "config has no tree_builder"), deck);
    }

    public static GameTree gameTreeFromJson(String jsonPath, Deck deck) {
        try {
            return new GameTree(jsonPath, deck);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GameTree gameTreeFromParams(
            Deck deck,
            float oopCommit,
            float ipCommit,
            int currentRound,
            int raiseLimit,
            float smallBlind,
            float bigBlind,
            float stack,
            GameTreeBuildingSettings buildingSettings) {
        return new GameTree(
                deck, oopCommit, ipCommit, currentRound, raiseLimit, smallBlind, bigBlind, stack, buildingSettings);
    }
}

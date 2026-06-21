package pokersolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import pokersolver.compairer.Compairer;
import pokersolver.compairer.Dic5Compairer;
import pokersolver.solver.GameTreeBuildingSettings;

/** Static factories assembling solver building blocks (deck, compairer, game tree) from configuration. */
public final class SolverEnvironment {

    private SolverEnvironment() {}

    public static GameTree gameTreeFromConfig(Config config, Deck deck) {
        try {
            return new GameTree(Objects.requireNonNull(config.treeBuilderJson), deck);
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
            GameTreeBuildingSettings gameTreeBuildingSettings) {
        try {
            return new GameTree(
                    deck,
                    oopCommit,
                    ipCommit,
                    currentRound,
                    raiseLimit,
                    smallBlind,
                    bigBlind,
                    stack,
                    gameTreeBuildingSettings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GameTree gameTreeFromJson(String jsonPath, Deck deck) {
        try {
            return new GameTree(jsonPath, deck);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Deck deckFromConfig(Config config) {
        return new Deck(Objects.requireNonNull(config.ranks), Objects.requireNonNull(config.suits));
    }

    public static Compairer compairerFromFile(String compairerType, String compairerDicDir, int compairerLines)
            throws IOException {
        if (compairerType.equals("Dic5Compairer")) {
            return new Dic5Compairer(compairerDicDir, compairerLines);
        }
        throw new IllegalArgumentException(String.format("compairer type not found: %s", compairerType));
    }

    public static Compairer compairerFromConfig(Config config) throws IOException {
        return compairerFromFile(
                Objects.requireNonNull(config.compairerType),
                Objects.requireNonNull(config.compairerDicDir),
                config.compairerLines);
    }

    public static Compairer compairerFromConfig(Config config, boolean verbose) throws IOException {
        if (Objects.requireNonNull(config.compairerType).equals("Dic5Compairer")) {
            return new Dic5Compairer(Objects.requireNonNull(config.compairerDicDir), config.compairerLines, verbose);
        }
        throw new IllegalArgumentException(String.format("compairer type not found: %s", config.compairerType));
    }
}

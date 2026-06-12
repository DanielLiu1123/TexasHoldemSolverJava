package icybee.solver;

import icybee.solver.compairer.Compairer;
import icybee.solver.compairer.Dic5Compairer;
import icybee.solver.solver.GameTreeBuildingSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Static factories assembling solver building blocks (deck, compairer, game tree) from configuration. */
public final class SolverEnvironment {

    private SolverEnvironment() {}

    public static GameTree gameTreeFromConfig(Config config, Deck deck) {
        try {
            return new GameTree(Objects.requireNonNull(config.tree_builder_json), deck);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GameTree gameTreeFromParams(
            Deck deck,
            float oop_commit,
            float ip_commit,
            int current_round,
            int raise_limit,
            float small_blind,
            float big_blind,
            float stack,
            GameTreeBuildingSettings gameTreeBuildingSettings) {
        try {
            return new GameTree(
                    deck,
                    oop_commit,
                    ip_commit,
                    current_round,
                    raise_limit,
                    small_blind,
                    big_blind,
                    stack,
                    gameTreeBuildingSettings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GameTree gameTreeFromJson(String json_path, Deck deck) {
        try {
            return new GameTree(json_path, deck);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Deck deckFromConfig(Config config) {
        return new Deck(Objects.requireNonNull(config.ranks), Objects.requireNonNull(config.suits));
    }

    public static Compairer compairerFromFile(String compairer_type, String compairer_dic_dir, int compairer_lines)
            throws IOException {
        if (compairer_type.equals("Dic5Compairer")) {
            return new Dic5Compairer(compairer_dic_dir, compairer_lines);
        }
        throw new IllegalArgumentException(String.format("compairer type not found: %s", compairer_type));
    }

    public static Compairer compairerFromConfig(Config config) throws IOException {
        return compairerFromFile(
                Objects.requireNonNull(config.compairer_type),
                Objects.requireNonNull(config.compairer_dic_dir),
                config.compairer_lines);
    }

    public static Compairer compairerFromConfig(Config config, boolean verbose) throws IOException {
        if (Objects.requireNonNull(config.compairer_type).equals("Dic5Compairer")) {
            return new Dic5Compairer(Objects.requireNonNull(config.compairer_dic_dir), config.compairer_lines, verbose);
        }
        throw new IllegalArgumentException(String.format("compairer type not found: %s", config.compairer_type));
    }
}

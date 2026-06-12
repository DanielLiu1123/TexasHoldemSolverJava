package icybee.solver.api;

import icybee.solver.Deck;
import icybee.solver.SolverEnvironment;
import icybee.solver.compairer.Compairer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lazily loads and caches the deck and compairer per game type. Dictionary loading is expensive
 * (the holdem dictionary has 2.6M entries), so each game type is loaded at most once per process.
 */
public final class GameResources {

    public record Loaded(Deck deck, Compairer compairer) {}

    private final Path resourcesDir;
    private final Map<GameType, Loaded> cache = new EnumMap<>(GameType.class);

    public GameResources(Path resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    public synchronized Loaded get(GameType game) {
        return cache.computeIfAbsent(game, this::load);
    }

    public synchronized List<String> loadedGames() {
        return cache.keySet().stream().map(GameType::id).toList();
    }

    private Loaded load(GameType game) {
        Path dictionary = resourcesDir.resolve(game.dictionaryFile());
        if (!Files.isRegularFile(dictionary)) {
            throw new IllegalStateException(String.format(
                    "compairer dictionary not found: %s",
                    dictionary.toAbsolutePath().normalize()));
        }
        Deck deck = new Deck(game.ranks(), GameType.SUITS);
        try {
            Compairer compairer =
                    SolverEnvironment.compairerFromFile("Dic5Compairer", dictionary.toString(), game.dictionaryLines());
            return new Loaded(deck, compairer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

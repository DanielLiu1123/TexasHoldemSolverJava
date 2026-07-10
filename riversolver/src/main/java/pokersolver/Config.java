package pokersolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * A solve scenario read from YAML: which deck to play with, and which game-tree file to solve.
 *
 * <p>Older configuration files also name a {@code compairer} dictionary. That key is ignored — hand
 * ranks are now derived from the deck rather than loaded from a file (see {@link
 * pokersolver.eval.HandEvaluator}) — so those files still load.
 */
public final class Config {

    private final List<String> ranks;
    private final List<String> suits;

    @Nullable
    private final String treeBuilderJson;

    public Config(String inputFile) {
        this(read(Path.of(inputFile)), Path.of(inputFile).toAbsolutePath().getParent());
    }

    public Config(InputStream inputStream, String baseDir) {
        this(read(inputStream), Path.of(baseDir));
    }

    @SuppressWarnings("unchecked")
    private Config(Map<String, Object> yaml, @Nullable Path baseDir) {
        Map<String, Object> deck = (Map<String, Object>) kwargs(yaml, "deck");
        this.ranks = (List<String>) Objects.requireNonNull(deck.get("rank"), "deck.kwargs.rank");
        this.suits = (List<String>) Objects.requireNonNull(deck.get("suit"), "deck.kwargs.suit");

        Object treeBuilder = yaml.get("tree_builder");
        if (treeBuilder == null) {
            this.treeBuilderJson = null;
        } else {
            Map<String, Object> kwargs = (Map<String, Object>) kwargs(yaml, "tree_builder");
            String jsonFile = (String) Objects.requireNonNull(kwargs.get("json_file"), "tree_builder.kwargs.json_file");
            this.treeBuilderJson = resolve(jsonFile, baseDir);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object kwargs(Map<String, Object> yaml, String section) {
        Map<String, Object> node =
                (Map<String, Object>) Objects.requireNonNull(yaml.get(section), () -> "missing section: " + section);
        return Objects.requireNonNull(node.get("kwargs"), () -> section + ".kwargs");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read config: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(InputStream in) {
        return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    }

    /** Resolves a path relative to the config file, then relative to that file's {@code src/test/resources}. */
    private static String resolve(String path, @Nullable Path baseDir) {
        Path candidate = Path.of(path);
        if (candidate.isAbsolute() || baseDir == null) return path;

        Path direct = baseDir.resolve(path).normalize();
        if (Files.exists(direct)) return direct.toString();

        Path testResources = baseDir.resolve("src/test/resources").resolve(path).normalize();
        if (Files.exists(testResources)) return testResources.toString();

        return path;
    }

    public List<String> ranks() {
        return ranks;
    }

    public List<String> suits() {
        return suits;
    }

    public @Nullable String treeBuilderJson() {
        return treeBuilderJson;
    }
}

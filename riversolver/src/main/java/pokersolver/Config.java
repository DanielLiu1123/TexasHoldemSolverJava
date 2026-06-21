package pokersolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Created by huangxuefeng on 2019/10/6.
 * Config parser
 */
public class Config {
    @Nullable
    List<String> ranks;

    @Nullable
    List<String> suits;

    // Compairer configures
    @Nullable
    String compairerType;

    @Nullable
    String compairerDicDir;

    int compairerLines;

    // Tree builder configures
    Boolean treeBuilder = false;

    @Nullable
    String treeBuilderJson;

    @Nullable
    String solverType;

    @SuppressWarnings({"unchecked", "NullAway"})
    public Config(String inputFile) throws FileNotFoundException, ClassNotFoundException {
        Yaml yamlReader = new Yaml(new SafeConstructor(new LoaderOptions()));
        File configFile = new File(inputFile);
        FileInputStream fileInputStream = new FileInputStream(configFile);
        Map map = yamlReader.load(fileInputStream);
        parseMap(map);
        resolveRelativePaths(configFile.getParentFile().getAbsolutePath());
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    public Config(InputStream inputStream, String baseDir) throws ClassNotFoundException {
        Yaml yamlReader = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map map = yamlReader.load(inputStream);
        parseMap(map);
        resolveRelativePaths(baseDir);
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    private void parseMap(Map map) {
        for (Object name : map.keySet()) {
            String key = name.toString();
            Object value = map.get(key);
            switch (key) {
                case "deck" -> {
                    Map deckdic = (Map) value;
                    ranks = (List<String>) ((Map) deckdic.get("kwargs")).get("rank");
                    suits = (List<String>) ((Map) deckdic.get("kwargs")).get("suit");
                }
                case "compairer" -> {
                    Map kwargs = (Map) ((Map) value).get("kwargs");
                    String dicDir = (String) kwargs.get("dicfile");
                    String type = (String) ((Map) value).get("type");
                    int lines = (Integer) kwargs.get("lines");
                    this.compairerDicDir = dicDir;
                    this.compairerType = type;
                    this.compairerLines = lines;
                }
                case "tree_builder" -> {
                    this.treeBuilder = true;
                    Map kwargs = (Map) ((Map) value).get("kwargs");
                    String jsonFile = (String) kwargs.get("json_file");
                    this.treeBuilderJson = jsonFile;
                }
                case "solver" -> {
                    String type = (String) ((Map) value).get("type");
                    solverType = type;
                }
            }
        }
    }

    private void resolveRelativePaths(String baseDir) {
        compairerDicDir = resolveResourcePath(compairerDicDir, baseDir);
    }

    private @Nullable String resolveResourcePath(@Nullable String path, String baseDir) {
        if (path == null || Paths.get(path).isAbsolute()) return path;
        // Try relative to baseDir directly (production distribution layout)
        Path resolved = Paths.get(baseDir).resolve(path).normalize();
        if (resolved.toFile().exists()) return resolved.toString();
        // Fallback: try src/test/resources/<path> relative to baseDir (IDE development layout)
        Path testFallback =
                Paths.get(baseDir).resolve("src/test/resources").resolve(path).normalize();
        if (testFallback.toFile().exists()) return testFallback.toString();
        return path;
    }
}

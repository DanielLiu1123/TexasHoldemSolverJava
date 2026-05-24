package icybee.solver;

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
    String compairer_type;

    @Nullable
    String compairer_dic_dir;

    int compairer_lines;

    // Tree builder configures
    Boolean tree_builder = false;

    @Nullable
    String tree_builder_json;

    @Nullable
    String solver_type;

    @SuppressWarnings({"unchecked", "NullAway"})
    public Config(String input_file) throws FileNotFoundException, ClassNotFoundException {
        Yaml yaml_reader = new Yaml(new SafeConstructor(new LoaderOptions()));
        File config_file = new File(input_file);
        FileInputStream fileInputStream = new FileInputStream(config_file);
        Map map = yaml_reader.load(fileInputStream);
        parseMap(map);
        resolveRelativePaths(config_file.getParentFile().getAbsolutePath());
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    public Config(InputStream inputStream, String baseDir) throws ClassNotFoundException {
        Yaml yaml_reader = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map map = yaml_reader.load(inputStream);
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
                    String dic_dir = (String) kwargs.get("dicfile");
                    String type = (String) ((Map) value).get("type");
                    int lines = (Integer) kwargs.get("lines");
                    this.compairer_dic_dir = dic_dir;
                    this.compairer_type = type;
                    this.compairer_lines = lines;
                }
                case "tree_builder" -> {
                    this.tree_builder = true;
                    Map kwargs = (Map) ((Map) value).get("kwargs");
                    String json_file = (String) kwargs.get("json_file");
                    this.tree_builder_json = json_file;
                }
                case "solver" -> {
                    String type = (String) ((Map) value).get("type");
                    solver_type = type;
                }
            }
        }
    }

    private void resolveRelativePaths(String baseDir) {
        compairer_dic_dir = resolveResourcePath(compairer_dic_dir, baseDir);
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

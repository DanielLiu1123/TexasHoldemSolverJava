package icybee.solver.gui;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * Resolves the application root directory at runtime, independent of the
 * working directory. Works in both IDE (class-directory) and distribution
 * (JAR inside lib/) layouts.
 */
final class AppPaths {

    private AppPaths() {}

    /**
     * Returns the application root:
     * - Distribution: parent of the {@code lib/} directory (i.e. the unzipped
     *   distribution root that contains {@code bin/}, {@code lib/},
     *   {@code compairer/}, {@code ranges/} …).
     * - IDE / Gradle run: the module directory that contains {@code src/}.
     */
    static Path getAppRoot() {
        var path = Path.of(".");
        try {
            URL location = AppPaths.class.getProtectionDomain().getCodeSource().getLocation();
            Path codePath = Path.of(location.toURI()).toAbsolutePath();
            if (codePath.toFile().isFile()) {
                // Running from a JAR inside lib/ → go up two levels to distribution root
                Path libDir = codePath.getParent();
                Path distRoot = libDir != null ? libDir.getParent() : null;
                if (distRoot != null) return distRoot;
            }
            // Running from an IDE class directory — walk up until we find a directory with src/
            Path p = codePath;
            while (p != null && !p.resolve("src").toFile().isDirectory()) {
                p = p.getParent();
            }
            return p != null ? p : path.toAbsolutePath();
        } catch (URISyntaxException e) {
            return path.toAbsolutePath();
        }
    }
}

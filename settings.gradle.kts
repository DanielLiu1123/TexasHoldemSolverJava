pluginManagement {
    val spotlessPluginVersion: String = providers.gradleProperty("spotlessPluginVersion").get()
    val errorPronePluginVersion: String = providers.gradleProperty("errorPronePluginVersion").get()
    val shadowPluginVersion: String = providers.gradleProperty("shadowPluginVersion").get()

    plugins {
        id("com.diffplug.spotless") version spotlessPluginVersion
        id("net.ltgt.errorprone") version errorPronePluginVersion
        id("com.gradleup.shadow") version shadowPluginVersion
    }
}

rootProject.name = "texas-holdem-solver"

include(":riversolver")

// Auto install git hooks
val hooksDir = File(rootDir, ".git/hooks")
if (hooksDir.exists() && hooksDir.isDirectory) {
    File(rootDir, ".githooks").listFiles()?.forEach { file ->
        if (file.isFile) {
            java.nio.file.Files.copy(
                file.toPath(),
                File(hooksDir, file.name).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

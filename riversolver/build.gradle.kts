plugins {
    id("application")
    id("com.gradleup.shadow")
}

description = "Texas Hold'em Poker Solver"

repositories {
    maven { setUrl("https://www.jetbrains.com/intellij-repository/releases") }
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

val antTask = configurations.register("antTask")

dependencies {
    implementation("org.yaml:snakeyaml:${providers.gradleProperty("snakeyamlVersion").get()}")
    implementation("me.tongfei:progressbar:${providers.gradleProperty("progressbarVersion").get()}")
    implementation("com.github.dpaukov:combinatoricslib3:${providers.gradleProperty("combinatoricslib3Version").get()}")
    implementation("tools.jackson.core:jackson-databind:${providers.gradleProperty("jacksonVersion").get()}")
    implementation("net.sourceforge.argparse4j:argparse4j:${providers.gradleProperty("argparse4jVersion").get()}")
    implementation("com.jetbrains.intellij.java:java-gui-forms-rt:${providers.gradleProperty("formsRtVersion").get()}")

    antTask("com.jetbrains.intellij.java:java-compiler-ant-tasks:+")
}


application {
    mainClass = "icybee.solver.gui.SolverGui"
    applicationName = "RiverSolver"
}

// see https://github.com/edward3h/systray-mpd/blob/master/build.gradle
tasks.named<JavaCompile>("compileJava") {
    notCompatibleWithConfigurationCache("Uses Ant tasks for IntelliJ form instrumentation")

//    dependsOn(configurations["implementation"].getTaskDependencyFromProjectDependency(true, "jar"))

    doLast {
        sourceSets["main"].output.classesDirs.forEach { project.mkdir(it) }

        ant.withGroovyBuilder {
            "taskdef"(
                "name" to "javac2",
                "classname" to "com.intellij.ant.Javac2",
                "classpath" to configurations["antTask"].asPath
            )
            "javac2"(
                "srcdir" to sourceSets["main"].java.srcDirs.joinToString(":"),
                "classpath" to sourceSets["main"].compileClasspath.asPath,
                "destdir" to sourceSets["main"].output.classesDirs.first(),
                "source" to java.sourceCompatibility,
                "target" to java.targetCompatibility,
                "includeAntRuntime" to false
            )
        }
    }
}

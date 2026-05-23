import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow")
}

description = "Texas Hold'em Poker Solver"

dependencies {
    implementation("org.yaml:snakeyaml:${providers.gradleProperty("snakeyamlVersion").get()}")
    implementation("me.tongfei:progressbar:${providers.gradleProperty("progressbarVersion").get()}")
    implementation("com.github.dpaukov:combinatoricslib3:${providers.gradleProperty("combinatoricslib3Version").get()}")
    implementation("tools.jackson.core:jackson-databind:${providers.gradleProperty("jacksonVersion").get()}")
    implementation("org.apache.commons:commons-lang3:${providers.gradleProperty("commonsLang3Version").get()}")
    implementation("net.sourceforge.argparse4j:argparse4j:${providers.gradleProperty("argparse4jVersion").get()}")
}

tasks.named<ShadowJar>("shadowJar") {
    manifest {
        attributes["Main-Class"] = "icybee.solver.gui.SolverGui"
    }
    archiveClassifier.set("")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

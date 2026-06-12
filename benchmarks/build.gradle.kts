import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("me.champeau.jmh")
}

description = "JMH benchmarks for the solver core"

// riversolver's GUI dependency (java-gui-forms-rt) is only resolvable from the
// JetBrains repositories, and repository declarations are per-project.
repositories {
    maven { setUrl("https://www.jetbrains.com/intellij-repository/releases") }
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

dependencies {
    jmhImplementation(project(":riversolver"))
}

jmh {
    jmhVersion = providers.gradleProperty("jmhVersion").get()
    resultFormat = "JSON"
    // The compairer dictionary and range files live in riversolver's test resources;
    // they are too large (50MB+) to duplicate into this module.
    jvmArgsAppend.add("-Dsolver.testResources=${rootDir.resolve("riversolver/src/test/resources")}")
    jvmArgsAppend.add("-Xmx2g")
    if (project.hasProperty("jmhIncludes")) {
        includes.add(project.property("jmhIncludes").toString())
    }
}

// JMH's annotation processor generates code we don't own — skip static analysis on it.
tasks.named<JavaCompile>("jmhCompileGeneratedClasses") {
    options.errorprone.enabled.set(false)
}

// Make `check` (and thus CI) verify the benchmarks still compile.
tasks.named("check") {
    dependsOn(tasks.named("jmhCompileGeneratedClasses"))
}

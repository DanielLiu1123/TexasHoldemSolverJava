import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("me.champeau.jmh")
}

description = "JMH benchmarks for the solver core"

dependencies {
    jmhImplementation(project(":solver-core"))
}

// The JMH bytecode generator reflects over benchmark classes in its own JVM.
tasks.withType<me.champeau.jmh.JmhBytecodeGeneratorTask>().configureEach {
    jvmArgs.add("--add-modules=jdk.incubator.vector")
}

jmh {
    jmhVersion = providers.gradleProperty("jmhVersion").get()
    resultFormat = "JSON"
    jvmArgsAppend.add("--add-modules=jdk.incubator.vector")
    jvmArgsAppend.add("-XX:+UseCompactObjectHeaders")
    if (project.hasProperty("jmhIncludes")) {
        includes.add(project.property("jmhIncludes").toString())
    }
    // e.g. -PjmhProfilers=gc,stack
    if (project.hasProperty("jmhProfilers")) {
        profilers.addAll(project.property("jmhProfilers").toString().split(","))
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

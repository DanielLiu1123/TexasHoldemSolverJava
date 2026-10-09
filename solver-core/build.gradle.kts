plugins {
    id("application")
    id("com.gradleup.shadow")
}

description = "Solver core: game-tree builder, CFR solvers, hand evaluator, CLI"

dependencies {
    api("org.slf4j:slf4j-api:${providers.gradleProperty("slf4jVersion").get()}")
    implementation("tools.jackson.core:jackson-databind:${providers.gradleProperty("jacksonVersion").get()}")
    implementation("net.sourceforge.argparse4j:argparse4j:${providers.gradleProperty("argparse4jVersion").get()}")

    runtimeOnly("org.slf4j:slf4j-simple:${providers.gradleProperty("slf4jVersion").get()}")
    testRuntimeOnly("org.slf4j:slf4j-simple:${providers.gradleProperty("slf4jVersion").get()}")
}

application {
    mainClass = "pokersolver.runtime.CommandlineSolver"
    applicationName = "poker-solver"
    applicationDefaultJvmArgs =
        listOf(
            "--add-modules=jdk.incubator.vector",
            // JEP 519: two-word object headers. The solver allocates float[] per node per
            // iteration, so a smaller header is a direct cut in allocation volume.
            "-XX:+UseCompactObjectHeaders",
        )
}

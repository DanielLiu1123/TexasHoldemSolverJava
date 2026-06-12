plugins {
    id("application")
    id("com.gradleup.shadow")
}

description = "Texas Hold'em Poker Solver"

dependencies {
    implementation("org.yaml:snakeyaml:${providers.gradleProperty("snakeyamlVersion").get()}")
    implementation("me.tongfei:progressbar:${providers.gradleProperty("progressbarVersion").get()}")
    implementation("com.github.dpaukov:combinatoricslib3:${providers.gradleProperty("combinatoricslib3Version").get()}")
    implementation("tools.jackson.core:jackson-databind:${providers.gradleProperty("jacksonVersion").get()}")
    implementation("net.sourceforge.argparse4j:argparse4j:${providers.gradleProperty("argparse4jVersion").get()}")
}

application {
    mainClass = "icybee.solver.runtime.CommandlineSolver"
    applicationName = "RiverSolver"
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.incubator.vector")
}

// Include data files in the distribution alongside the JAR.
distributions {
    main {
        contents {
            from("src/test/resources/compairer") {
                into("compairer")
            }
            from("src/test/resources/ranges") {
                into("ranges")
            }
        }
    }
}

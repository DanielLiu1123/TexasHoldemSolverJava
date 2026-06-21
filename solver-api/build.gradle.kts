plugins {
    id("application")
}

description = "Embedded HTTP API exposing the solver core (ADR 0001)"

val webDist by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(project(":riversolver"))
    implementation("io.javalin:javalin:${providers.gradleProperty("javalinVersion").get()}")
    implementation("tools.jackson.core:jackson-databind:${providers.gradleProperty("jacksonVersion").get()}")
    runtimeOnly("org.slf4j:slf4j-simple:${providers.gradleProperty("slf4jVersion").get()}")

    testImplementation("org.slf4j:slf4j-simple:${providers.gradleProperty("slf4jVersion").get()}")

    webDist(project(path = ":web-ui", configuration = "webDist"))
}

// Embed the built web UI under /web on the classpath; ApiServer serves it.
tasks.processResources {
    from(webDist) {
        into("web")
    }
}

application {
    mainClass = "pokersolver.api.ApiServer"
    applicationName = "solver-api"
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.incubator.vector")
}

// JavaExec defaults workingDir to this subproject; resolve --resources paths
// against the repository root instead, matching the README invocation.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

tasks.withType<Test>().configureEach {
    // Integration tests load the compairer dictionaries from riversolver's test resources.
    systemProperty("solver.testResources", rootDir.resolve("riversolver/src/test/resources").toString())
}

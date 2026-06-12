plugins {
    id("application")
}

description = "Embedded HTTP API exposing the solver core (ADR 0001)"

// riversolver's GUI dependency (java-gui-forms-rt) is only resolvable from the
// JetBrains repositories, and repository declarations are per-project.
repositories {
    maven { setUrl("https://www.jetbrains.com/intellij-repository/releases") }
    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

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
    mainClass = "icybee.solver.api.ApiServer"
    applicationName = "solver-api"
}

tasks.withType<Test>().configureEach {
    // Integration tests load the compairer dictionaries from riversolver's test resources.
    systemProperty("solver.testResources", rootDir.resolve("riversolver/src/test/resources").toString())
}

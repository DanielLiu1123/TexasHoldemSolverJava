import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("com.github.node-gradle.node")
}

description = "Web UI for the solver (served by solver-api)"

node {
    version = providers.gradleProperty("nodeVersion").get()
    download = true
}

val viteBuild = tasks.register<NpmTask>("viteBuild") {
    dependsOn(tasks.npmInstall)
    args = listOf("run", "build")
    inputs.dir("src")
    inputs.files("index.html", "package.json", "package-lock.json", "vite.config.ts", "tsconfig.json")
    outputs.dir(layout.projectDirectory.dir("dist"))
}

val vitest = tasks.register<NpmTask>("vitest") {
    dependsOn(tasks.npmInstall)
    args = listOf("run", "test")
    inputs.dir("src")
    inputs.files("package.json", "package-lock.json", "vite.config.ts", "tsconfig.json")
    // vitest writes no artifacts; track success via the build directory marker
    outputs.upToDateWhen { false }
}

tasks.register("check") {
    dependsOn(vitest)
}

tasks.register("build") {
    dependsOn(viteBuild, "check")
}

// Exposes the built web assets to other modules (solver-api embeds them).
configurations.consumable("webDist")

artifacts {
    add("webDist", layout.projectDirectory.dir("dist")) {
        builtBy(viteBuild)
    }
}

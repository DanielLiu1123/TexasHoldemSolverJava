import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.diffplug.spotless") apply false
    id("net.ltgt.errorprone") apply false
    id("com.gradleup.shadow") apply false
}

val errorProneCoreVersion: String = providers.gradleProperty("errorProneCoreVersion").get()
val nullAwayVersion: String = providers.gradleProperty("nullAwayVersion").get()
val junitVersion: String = providers.gradleProperty("junitVersion").get()

subprojects {
    plugins.apply("java")
    plugins.apply("java-library")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.toVersion(25)
        targetCompatibility = JavaVersion.toVersion(25)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-parameters", "-XDaddTypeAnnotationsToSymbol=true"))
        options.encoding = "UTF-8"
    }

    tasks.withType<Javadoc>().configureEach {
        (options as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        "compileOnly"("org.jspecify:jspecify:1.0.0")
        "testCompileOnly"("org.jspecify:jspecify:1.0.0")

        "testImplementation"(platform("org.junit:junit-bom:$junitVersion"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.assertj:assertj-core:+")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        // The holdem compairer dictionary (2.6M boxed map entries) alone exceeds
        // Gradle's default 512m test-worker heap.
        maxHeapSize = "2g"
    }

    plugins.apply("com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            targetExclude("**/generated/**")
            toggleOffOn()
            removeUnusedImports()
            forbidWildcardImports()
            importOrder()
            formatAnnotations()
            trimTrailingWhitespace()
            endWithNewline()
            palantirJavaFormat()
        }
    }

    plugins.apply("net.ltgt.errorprone")
    dependencies {
        "errorprone"("com.google.errorprone:error_prone_core:$errorProneCoreVersion")
        "errorprone"("com.uber.nullaway:nullaway:$nullAwayVersion")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            // Note: must target ErrorProneOptions.enabled, not Task.enabled —
            // `isEnabled` here silently binds to the enclosing task and would
            // disable compilation entirely when the property is set to false.
            enabled.set(project.findProperty("errorprone.enabled")?.toString()?.toBoolean() != false)
            excludedPaths = ".*/generated/.*"
            check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "icybee.solver")
            option("NullAway:HandleTestAssertionLibraries", "true")
        }
    }
}

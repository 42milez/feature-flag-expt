plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

group = "com.github.milez42"
version = "0.1.0"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            // Keep Kotlin calls to Java/Spring APIs aligned with their declared nullability contracts.
            "-Xjsr305=strict",
            // Preserve Kotlin type-use annotations so Bean Validation can enforce container element
            // constraints such as Set<@NotBlank @Size(...) String> on request DTOs.
            "-Xemit-jvm-type-annotations",
        )
    }
}

spotless {
    java {
        googleJavaFormat()
    }
    kotlin {
        ktfmt("0.62")
    }
    kotlinGradle {
        ktfmt("0.62")
    }
}

// Spotless 8.3.0+ is not compatible with configuration cache (issue #2878)
tasks.withType<com.diffplug.gradle.spotless.SpotlessTask>().configureEach {
    notCompatibleWithConfigurationCache("Spotless is not compatible with configuration cache")
}

dependencies {
    // Version catalog type-safe accessors (libs.xxx) are unavailable in precompiled script plugins
    errorprone("com.google.errorprone:error_prone_core:2.49.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

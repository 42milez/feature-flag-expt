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
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

spotless {
    java {
        googleJavaFormat()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
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

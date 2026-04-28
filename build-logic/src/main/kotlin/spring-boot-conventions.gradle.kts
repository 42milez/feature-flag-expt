plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.github.milez42"
version = "0.1.0"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

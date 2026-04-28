plugins {
    id("spring-boot-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.spring.boot.starter.test)
}

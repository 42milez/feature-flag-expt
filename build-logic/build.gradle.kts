plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.error.prone.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}

// Plugins add capabilities and conventions to the Gradle build itself.
plugins {
  id("spring-boot-conventions")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.springdoc.openapi)
  alias(libs.plugins.kotlin.spring)
}

extra["tomcat.version"] = libs.versions.tomcat.get()

// Dependencies add libraries used by the application at compile time, runtime, or during tests.
dependencies {
  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.data.jdbc)
  implementation(libs.spring.boot.starter.security)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.boot.starter.web)
  implementation(libs.spring.boot.flyway)
  implementation(libs.springdoc.openapi.starter.webmvc.ui)
  implementation(libs.kotlin.reflect)
  implementation(libs.flyway.core)
  implementation(libs.flyway.database.postgresql)
  developmentOnly(libs.h2)
  runtimeOnly(libs.micrometer.registry.prometheus)
  runtimeOnly(libs.postgresql)
  testImplementation(libs.mockk)
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.security.test)
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.postgresql)
  constraints {
    implementation(libs.tomcat.embed.core)
    implementation(libs.tomcat.embed.el)
    implementation(libs.tomcat.embed.websocket)
  }
}

// Starts the application with the OpenAPI generation profile and writes its API specification to
// docs/openapi.yaml.
openApi {
  apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
  outputDir.set(rootProject.layout.projectDirectory.dir("docs"))
  outputFileName.set("openapi.yaml")
  customBootRun { args.set(listOf("--spring.profiles.active=openapi-gen")) }
  waitTimeInSeconds.set(30)
}

// The springdoc plugin's forked tasks capture Gradle task instances, which cannot be serialized
// for the configuration cache. Mark them incompatible so OpenAPI generation does not fail while
// Gradle attempts to store the cache.
tasks.named("forkedSpringBootRun") {
  notCompatibleWithConfigurationCache(
      "springdoc-openapi-gradle-plugin fork task captures task instances"
  )
}

tasks.named("forkedSpringBootStop") {
  notCompatibleWithConfigurationCache(
      "springdoc-openapi-gradle-plugin stop task captures task instances"
  )
}

// Rename the bootJar output to a fixed file name.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName.set("feature-flag-platform.jar")
}

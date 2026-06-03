plugins {
  id("spring-boot-conventions")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.springdoc.openapi)
  alias(libs.plugins.kotlin.spring)
}

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
}

openApi {
  apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
  outputDir.set(rootProject.layout.projectDirectory.dir("docs"))
  outputFileName.set("openapi.yaml")
  customBootRun { args.set(listOf("--spring.profiles.active=openapi-gen")) }
  waitTimeInSeconds.set(30)
}

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

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName.set("feature-flag-platform.jar")
}

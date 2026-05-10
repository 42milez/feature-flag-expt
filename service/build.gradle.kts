plugins {
  id("spring-boot-conventions")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.spring)
}

dependencies {
  implementation(libs.spring.boot.starter.data.jdbc)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.boot.starter.web)
  implementation(libs.spring.boot.flyway)
  implementation(libs.springdoc.openapi.starter.webmvc.ui)
  implementation(libs.kotlin.reflect)
  implementation(libs.flyway.core)
  implementation(libs.flyway.database.postgresql)
  runtimeOnly(libs.postgresql)
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.postgresql)
}

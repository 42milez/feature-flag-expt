package com.github.milez42.featureflags.policy

import com.github.milez42.featureflags.OpenApiConfig
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(
    classes = [RolloutPolicyOpenApiIntegrationTest.TestApplication::class],
    properties =
        [
            "spring.autoconfigure.exclude=" +
                "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration," +
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration"
        ],
)
class RolloutPolicyOpenApiIntegrationTest {
  @Autowired private lateinit var webApplicationContext: WebApplicationContext

  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
  }

  @Test
  fun apiDocsJsonIncludesRolloutPolicyPathAndSchemas() {
    val result =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/flags/{flagKey}/validate-change'].post").exists())
            .andExpect(
                jsonPath(
                        "$.paths['/api/flags/{flagKey}/validate-change'].post.parameters[0].schema.minLength"
                    )
                    .value(1)
            )
            .andExpect(
                jsonPath(
                        "$.components.schemas.RolloutPolicyValidationRequest.properties.reason.maxLength"
                    )
                    .value(1000)
            )
            .andExpect(
                jsonPath("$.components.schemas.RolloutPolicyViolation.properties.code").exists()
            )
            .andExpect(
                jsonPath("$.components.schemas.RolloutPolicyViolation.properties.message").exists()
            )
            .andExpect(
                jsonPath("$.components.schemas.RolloutPolicyViolation.properties.severity").exists()
            )
            .andExpect(jsonPath("$.components.schemas.Severity.enum", hasSize<Any>(1)))
            .andExpect(jsonPath("$.components.schemas.Severity.enum", contains("ERROR")))
            .andReturn()

    assertThat(result.response.contentAsString)
        .contains(
            "\"RolloutPolicyValidationRequest\"",
            "\"RolloutPolicyValidationResponse\"",
            "\"RolloutPolicyViolation\"",
            "\"Severity\"",
        )
  }

  @Test
  fun apiDocsYamlIncludesRolloutPolicyPath() {
    val result = mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk).andReturn()

    assertThat(result.response.contentAsString)
        .contains("openapi:", "Feature Flag API", "/api/flags/{flagKey}/validate-change:")
  }

  @Configuration
  @EnableAutoConfiguration
  @Import(RolloutPolicyController::class, OpenApiConfig::class)
  class TestApplication {
    @Bean fun rolloutPolicyValidationService(): RolloutPolicyValidationService = mockk()
  }
}

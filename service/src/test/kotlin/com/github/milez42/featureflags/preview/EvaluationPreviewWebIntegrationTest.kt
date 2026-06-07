package com.github.milez42.featureflags.preview

import com.github.milez42.featureflags.OpenApiConfig
import com.github.milez42.featureflags.SecurityConfig
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(
    classes = [EvaluationPreviewWebIntegrationTest.TestApplication::class],
    properties =
        [
            "spring.autoconfigure.exclude=" +
                "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration," +
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration"
        ],
)
class EvaluationPreviewWebIntegrationTest {
  @Autowired private lateinit var webApplicationContext: WebApplicationContext

  @Autowired private lateinit var evaluationPreviewService: EvaluationPreviewService

  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
  }

  @Test
  fun apiDocsJsonIncludesPreviewPathAndSchemas() {
    val result =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/flags/{flagKey}/preview'].post").exists())
            .andExpect(
                jsonPath(
                        "$.paths['/api/flags/{flagKey}/preview'].post.parameters[0].schema.minLength"
                    )
                    .value(1)
            )
            .andExpect(
                jsonPath(
                        "$.components.schemas.EvaluationPreviewRequest.properties.sampleContexts.maxItems"
                    )
                    .value(100)
            )
            .andExpect(
                jsonPath(
                        "$.components.schemas.ProposedFeatureFlagChange.properties.tenantAllowlist.maxItems"
                    )
                    .value(1000)
            )
            .andExpect(
                jsonPath(
                        "$.components.schemas.EvaluationPreviewContext.properties.environment.minLength"
                    )
                    .value(1)
            )
            .andExpect(
                jsonPath(
                        "$.components.schemas.ProposedFeatureFlagChange.properties.tenantAllowlist.items.minLength"
                    )
                    .value(1)
            )
            .andReturn()

    assertThat(result.response.contentAsString)
        .contains(
            "\"EvaluationPreviewRequest\"",
            "\"EvaluationPreviewResponse\"",
            "\"EvaluationDiff\"",
            "\"EvaluationPreviewSummary\"",
            "Empty input clears target environments",
        )
  }

  @Test
  fun previewRequestBindsKotlinDtoInWebContext() {
    every {
      evaluationPreviewService.preview("checkout-redesign", any<EvaluationPreviewRequest>())
    } returns
        EvaluationPreviewResponse(
            "checkout-redesign",
            emptyList(),
            EvaluationPreviewSummary(0, 0, 0, 0, 0, 0),
        )

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .with(httpBasic("test-reader", "test-reader-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "proposedChange": {
                        "targetEnvironments": ["production"],
                        "rolloutPercentage": 50
                      },
                      "sampleContexts": [
                        {
                          "environment": "production",
                          "tenantId": "tenant-a",
                          "userId": "user-a"
                        }
                      ]
                    }
                    """
                        .trimIndent()
                )
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"))
  }

  @Configuration
  @EnableAutoConfiguration
  @Import(EvaluationPreviewController::class, OpenApiConfig::class, SecurityConfig::class)
  class TestApplication {
    @Bean fun evaluationPreviewService(): EvaluationPreviewService = mockk()
  }
}

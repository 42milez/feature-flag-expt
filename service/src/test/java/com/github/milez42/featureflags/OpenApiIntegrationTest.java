package com.github.milez42.featureflags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.milez42.featureflags.flags.FeatureFlagController;
import com.github.milez42.featureflags.flags.FeatureFlagService;
import com.github.milez42.featureflags.preview.EvaluationPreviewController;
import com.github.milez42.featureflags.preview.EvaluationPreviewRequest;
import com.github.milez42.featureflags.preview.EvaluationPreviewResponse;
import com.github.milez42.featureflags.preview.EvaluationPreviewService;
import com.github.milez42.featureflags.preview.EvaluationPreviewSummary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = OpenApiIntegrationTest.TestApplication.class,
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
class OpenApiIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private EvaluationPreviewService evaluationPreviewService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void swaggerUiIsAccessible() throws Exception {
    MvcResult result = mockMvc.perform(get("/swagger-ui.html")).andReturn();

    assertThat(result.getResponse().getStatus()).isIn(200, 302);
  }

  @Test
  void apiDocsJsonIncludesTargetPathsAndSchemas() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Feature Flag API"))
            .andExpect(jsonPath("$.info.version").value("0.1.0"))
            .andExpect(jsonPath("$.paths['/api/flags'].post").exists())
            .andExpect(jsonPath("$.paths['/api/flags/{flagKey}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/flags/{flagKey}'].patch").exists())
            .andExpect(jsonPath("$.paths['/api/evaluate'].post").exists())
            .andExpect(jsonPath("$.paths['/api/flags/{flagKey}/audit-events'].get").exists())
            .andExpect(jsonPath("$.paths['/api/flags/{flagKey}/preview'].post").exists())
            .andExpect(
                jsonPath("$.paths['/api/flags/{flagKey}'].get.parameters[0].schema.minLength")
                    .value(1))
            .andExpect(
                jsonPath("$.paths['/api/flags/{flagKey}'].patch.parameters[0].schema.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.paths['/api/flags/{flagKey}/audit-events'].get.parameters[0].schema.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.paths['/api/flags/{flagKey}/preview'].post.parameters[0].schema.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.EvaluationPreviewRequest.properties.sampleContexts.maxItems")
                    .value(100))
            .andExpect(
                jsonPath(
                        "$.components.schemas.ProposedFeatureFlagChange.properties.tenantAllowlist.maxItems")
                    .value(1000))
            .andExpect(
                jsonPath(
                        "$.components.schemas.CreateFeatureFlagRequest.properties.flagKey.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.CreateFeatureFlagRequest.properties.tenantAllowlist.items.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.EvaluationPreviewContext.properties.environment.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.ProposedFeatureFlagChange.properties.tenantAllowlist.items.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.EvaluateFeatureFlagRequest.properties.flagKey.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.EvaluateFeatureFlagRequest.properties.environment.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.UpdateFeatureFlagRequest.properties.tenantAllowlist.items.minLength")
                    .value(1))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .contains(
            "\"CreateFeatureFlagRequest\"",
            "\"UpdateFeatureFlagRequest\"",
            "\"FeatureFlagResponse\"",
            "\"EvaluateFeatureFlagRequest\"",
            "\"EvaluateFeatureFlagResponse\"",
            "\"EvaluationPreviewRequest\"",
            "\"EvaluationPreviewResponse\"",
            "\"EvaluationDiff\"",
            "\"EvaluationPreviewSummary\"",
            "\"AuditEventResponse\"",
            "\"ProblemDetail\"",
            "\"TARGET_ENVIRONMENTS_CHANGED\"",
            "Empty input clears target environments")
        .doesNotContain("Empty input preserves existing values");
  }

  @Test
  void apiDocsYamlIsAvailable() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk()).andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains(
            "openapi:",
            "Feature Flag API",
            "/api/flags/{flagKey}/audit-events:",
            "/api/flags/{flagKey}/preview:");
  }

  @Test
  void previewRequestBindsKotlinDtoInWebContext() throws Exception {
    when(evaluationPreviewService.preview(
            eq("checkout-redesign"), any(EvaluationPreviewRequest.class)))
        .thenReturn(
            new EvaluationPreviewResponse(
                "checkout-redesign", List.of(), new EvaluationPreviewSummary(0, 0, 0, 0, 0, 0)));

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
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
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"));
  }

  @Configuration
  @EnableAutoConfiguration
  @Import({FeatureFlagController.class, EvaluationPreviewController.class, OpenApiConfig.class})
  static class TestApplication {
    @Bean
    FeatureFlagService featureFlagService() {
      return mock(FeatureFlagService.class);
    }

    @Bean
    EvaluationPreviewService evaluationPreviewService() {
      return mock(EvaluationPreviewService.class);
    }
  }
}

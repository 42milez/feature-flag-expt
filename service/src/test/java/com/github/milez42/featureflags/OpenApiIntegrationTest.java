package com.github.milez42.featureflags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.milez42.featureflags.flags.FeatureFlagController;
import com.github.milez42.featureflags.flags.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
                        "$.components.schemas.CreateFeatureFlagRequest.properties.flagKey.minLength")
                    .value(1))
            .andExpect(
                jsonPath(
                        "$.components.schemas.CreateFeatureFlagRequest.properties.tenantAllowlist.items.minLength")
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
            "\"AuditEventResponse\"",
            "\"ProblemDetail\"",
            "\"TARGET_ENVIRONMENTS_CHANGED\"")
        .doesNotContain("Empty input preserves existing values");
  }

  @Test
  void apiDocsYamlIsAvailable() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk()).andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("openapi:", "Feature Flag API", "/api/flags/{flagKey}/audit-events:");
  }

  @Configuration
  @EnableAutoConfiguration
  @Import({FeatureFlagController.class, OpenApiConfig.class})
  static class TestApplication {
    @Bean
    FeatureFlagService featureFlagService() {
      return mock(FeatureFlagService.class);
    }
  }
}

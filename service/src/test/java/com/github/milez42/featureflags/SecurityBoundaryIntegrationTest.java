package com.github.milez42.featureflags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.milez42.featureflags.flags.CreateFeatureFlagRequest;
import com.github.milez42.featureflags.flags.FeatureFlagController;
import com.github.milez42.featureflags.flags.FeatureFlagResponse;
import com.github.milez42.featureflags.flags.FeatureFlagService;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import java.util.Set;
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
    classes = SecurityBoundaryIntegrationTest.TestApplication.class,
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
class SecurityBoundaryIntegrationTest {
  private static final String TEST_USERNAME = "test-user";
  private static final String TEST_PASSWORD = "test-password";

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private FeatureFlagService featureFlagService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(featureFlagService);
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void apiRequestsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/flags/checkout-redesign")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedApiRequestSucceeds() throws Exception {
    when(featureFlagService.create(any(CreateFeatureFlagRequest.class)))
        .thenReturn(
            new FeatureFlagResponse(
                "checkout-redesign",
                FeatureFlagStatus.ENABLED,
                Set.of("production"),
                false,
                Set.of(),
                25));

    mockMvc
        .perform(
            post("/api/flags")
                .with(httpBasic(TEST_USERNAME, TEST_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "flagKey": "checkout-redesign",
                      "status": "ENABLED",
                      "targetEnvironments": ["production"],
                      "killSwitchActive": false,
                      "rolloutPercentage": 25
                    }
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void healthEndpointsRemainPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  void prometheusRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/actuator/prometheus").with(httpBasic(TEST_USERNAME, TEST_PASSWORD)))
        .andExpect(status().isOk());
  }

  @Test
  void openApiAndSwaggerRemainPublic() throws Exception {
    MvcResult swaggerUi = mockMvc.perform(get("/swagger-ui.html")).andReturn();

    assertThat(swaggerUi.getResponse().getStatus()).isIn(200, 302);
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
  }

  @Test
  void unlistedRoutesRequireAuthentication() throws Exception {
    mockMvc.perform(get("/unlisted")).andExpect(status().isUnauthorized());
  }

  @Configuration
  @EnableAutoConfiguration
  @Import({FeatureFlagController.class, OpenApiConfig.class, SecurityConfig.class})
  static class TestApplication {
    @Bean
    FeatureFlagService featureFlagService() {
      return mock(FeatureFlagService.class);
    }
  }
}

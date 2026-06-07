package com.github.milez42.featureflags;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.milez42.featureflags.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ActuatorHealthIntegrationTest extends PostgreSqlIntegrationTest {
  private static final String TEST_USERNAME = "test-user";
  private static final String TEST_PASSWORD = "test-password";

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            // Register the Security filter chain so unauthenticated health checks prove they pass
            // because SecurityConfig permits them, not because MockMvc bypassed Spring Security.
            .apply(springSecurity())
            .build();
  }

  @Test
  void healthEndpointReturnsOk() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void livenessEndpointReturnsOk() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }

  @Test
  void readinessEndpointReturnsOk() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  void prometheusEndpointReturnsOk() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus").with(httpBasic(TEST_USERNAME, TEST_PASSWORD)))
        .andExpect(status().isOk());
  }
}

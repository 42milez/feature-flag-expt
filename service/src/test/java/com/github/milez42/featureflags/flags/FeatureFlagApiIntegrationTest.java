package com.github.milez42.featureflags.flags;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FeatureFlagApiIntegrationTest extends PostgreSqlIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private FeatureFlagRepository repository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void createFlagReturnsCreatedFlagAndLocation() throws Exception {
    mockMvc
        .perform(
            post("/api/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "status": "ENABLED",
                                  "targetEnvironments": ["production", "staging"],
                                  "killSwitchActive": false,
                                  "tenantAllowlist": ["tenant-a"],
                                  "rolloutPercentage": 25
                                }
                                """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/flags/checkout-redesign"))
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"))
        .andExpect(jsonPath("$.status").value("ENABLED"))
        .andExpect(jsonPath("$.targetEnvironments", containsInAnyOrder("production", "staging")))
        .andExpect(jsonPath("$.tenantAllowlist", containsInAnyOrder("tenant-a")))
        .andExpect(jsonPath("$.rolloutPercentage").value(25));
  }

  @Test
  void duplicateCreateReturnsConflict() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "status": "ENABLED",
                                  "killSwitchActive": false,
                                  "rolloutPercentage": 25
                                }
                                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Feature flag conflict"));
  }

  @Test
  void getFlagReturnsPersistedData() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(get("/api/flags/checkout-redesign"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"))
        .andExpect(jsonPath("$.status").value("ENABLED"))
        .andExpect(jsonPath("$.targetEnvironments", containsInAnyOrder("production", "staging")))
        .andExpect(jsonPath("$.tenantAllowlist", containsInAnyOrder("tenant-a")))
        .andExpect(jsonPath("$.rolloutPercentage").value(100));
  }

  @Test
  void missingFlagReturnsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/flags/missing-flag"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Feature flag not found"));
  }

  @Test
  void patchUpdatesOnlySuppliedFields() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "killSwitchActive": true,
                                  "rolloutPercentage": 50
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENABLED"))
        .andExpect(jsonPath("$.targetEnvironments", containsInAnyOrder("production", "staging")))
        .andExpect(jsonPath("$.tenantAllowlist", containsInAnyOrder("tenant-a")))
        .andExpect(jsonPath("$.killSwitchActive").value(true))
        .andExpect(jsonPath("$.rolloutPercentage").value(50));
  }

  @Test
  void patchWithEmptySetsClearsCollections() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "targetEnvironments": [],
                                  "tenantAllowlist": []
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetEnvironments").isEmpty())
        .andExpect(jsonPath("$.tenantAllowlist").isEmpty());
  }

  @Test
  void validationFailureReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "",
                                  "status": "ENABLED",
                                  "rolloutPercentage": 101
                                }
                                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void evaluateReadsFlagFromDatabase() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "environment": "production",
                                  "tenantId": "tenant-z",
                                  "userId": "user-a"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.reason").value("ROLLOUT_MATCH"))
        .andExpect(jsonPath("$.bucket", notNullValue()));
  }

  @Test
  void evaluateMissingFlagReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "missing-flag",
                                  "environment": "production",
                                  "tenantId": "tenant-z"
                                }
                                """))
        .andExpect(status().isNotFound());
  }

  private void createCheckoutFlag() throws Exception {
    mockMvc
        .perform(
            post("/api/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "status": "ENABLED",
                                  "targetEnvironments": ["production", "staging"],
                                  "killSwitchActive": false,
                                  "tenantAllowlist": ["tenant-a"],
                                  "rolloutPercentage": 100
                                }
                                """))
        .andExpect(status().isCreated());
  }
}

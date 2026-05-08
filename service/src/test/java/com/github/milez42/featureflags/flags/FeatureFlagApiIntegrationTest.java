package com.github.milez42.featureflags.flags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.milez42.featureflags.audit.AuditEvent;
import com.github.milez42.featureflags.audit.AuditEventDetails;
import com.github.milez42.featureflags.audit.AuditEventRepository;
import com.github.milez42.featureflags.audit.AuditEventType;
import com.github.milez42.featureflags.support.PostgreSqlIntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FeatureFlagApiIntegrationTest extends PostgreSqlIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private FeatureFlagRepository repository;

  @Autowired private AuditEventRepository auditEventRepository;

  @Autowired private JdbcClient jdbcClient;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("delete from audit_events").update();
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

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.eventType()).isEqualTo(AuditEventType.FLAG_CREATED);
              assertThat(event.details())
                  .isEqualTo(
                      new AuditEventDetails.FlagCreatedDetails(
                          FeatureFlagStatus.ENABLED,
                          25,
                          false,
                          Set.of("production", "staging"),
                          Set.of("tenant-a")));
            });
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
                                  "targetEnvironments": ["production"],
                                  "killSwitchActive": false,
                                  "rolloutPercentage": 25
                                }
                                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Feature flag conflict"));

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED);
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

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(
            AuditEventType.FLAG_CREATED,
            AuditEventType.ROLLOUT_PERCENTAGE_CHANGED,
            AuditEventType.KILL_SWITCH_ENABLED);
  }

  @Test
  void patchWithEmptyTenantAllowlistClearsAllowlist() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "tenantAllowlist": []
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantAllowlist").isEmpty());

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED, AuditEventType.TENANT_ALLOWLIST_CHANGED);
  }

  @Test
  void patchWithEmptyTargetEnvironmentsPreservesExisting() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "targetEnvironments": []
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetEnvironments", containsInAnyOrder("production", "staging")));

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED);
  }

  @Test
  void patchStatusRecordsEnabledAndDisabledEvents() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "status": "DISABLED"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "status": "ENABLED"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENABLED"));

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(
            AuditEventType.FLAG_CREATED, AuditEventType.FLAG_DISABLED, AuditEventType.FLAG_ENABLED);
  }

  @Test
  void patchKillSwitchFalseRecordsDisabledEvent() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "killSwitchActive": true
                                }
                                """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "killSwitchActive": false
                                }
                                """))
        .andExpect(status().isOk());

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(
            AuditEventType.FLAG_CREATED,
            AuditEventType.KILL_SWITCH_ENABLED,
            AuditEventType.KILL_SWITCH_DISABLED);
  }

  @Test
  void patchWithUnchangedValuesDoesNotRecordAuditEvent() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "status": "ENABLED",
                                  "killSwitchActive": false,
                                  "tenantAllowlist": ["tenant-a"],
                                  "rolloutPercentage": 100
                                }
                                """))
        .andExpect(status().isOk());

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED);
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
  void unknownEnvironmentReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "status": "ENABLED",
                                  "targetEnvironments": ["unknown-env"],
                                  "killSwitchActive": false,
                                  "rolloutPercentage": 25
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

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED);
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

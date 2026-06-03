package com.github.milez42.featureflags.flags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class FeatureFlagApiIntegrationTest extends PostgreSqlIntegrationTest {
  private static final String TEST_USERNAME = "test-user";
  private static final String TEST_PASSWORD = "test-password";

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private FeatureFlagRepository repository;

  @Autowired private AuditEventRepository auditEventRepository;

  @Autowired private JdbcClient jdbcClient;

  @Autowired private ObjectMapper objectMapper;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("delete from audit_events").update();
    repository.deleteAll();
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .defaultRequest(get("/").with(httpBasic(TEST_USERNAME, TEST_PASSWORD)))
            .apply(springSecurity())
            .build();
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
  void auditEventsReturnsEventsOldestFirst() throws Exception {
    createPolicyCompliantCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "rolloutPercentage": 50
                                }
                                """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/flags/checkout-redesign/audit-events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", notNullValue()))
        .andExpect(jsonPath("$[0].flagKey").value("checkout-redesign"))
        .andExpect(jsonPath("$[0].eventType").value("FLAG_CREATED"))
        .andExpect(jsonPath("$[0].details.status").value("ENABLED"))
        .andExpect(jsonPath("$[0].occurredAt", notNullValue()))
        .andExpect(jsonPath("$[1].eventType").value("ROLLOUT_PERCENTAGE_CHANGED"))
        .andExpect(jsonPath("$[1].details.field").value("rolloutPercentage"))
        .andExpect(jsonPath("$[1].details.oldValue").value(100))
        .andExpect(jsonPath("$[1].details.newValue").value(50));
  }

  @Test
  void auditEventsMissingFlagReturnsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/flags/missing-flag/audit-events"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Feature flag not found"));
  }

  @Test
  void auditEventsExistingFlagWithoutEventsReturnsEmptyList() throws Exception {
    createCheckoutFlag();
    jdbcClient.sql("delete from audit_events").update();

    mockMvc
        .perform(get("/api/flags/checkout-redesign/audit-events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
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
    createPolicyCompliantCheckoutFlag();

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
  void patchWithEmptyTargetEnvironmentsClearsTargetEnvironments() throws Exception {
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
        .andExpect(jsonPath("$.targetEnvironments").isEmpty());

    var events = auditEventRepository.findByFlagKey("checkout-redesign");
    assertThat(events)
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED, AuditEventType.TARGET_ENVIRONMENTS_CHANGED);
    assertThat(events.get(1).details())
        .isEqualTo(
            new AuditEventDetails.TargetEnvironmentsChangedDetails(
                "targetEnvironments", Set.of("production", "staging"), Set.of()));
  }

  @Test
  void patchWithTargetEnvironmentsReplacementRecordsAuditEvent() throws Exception {
    createPolicyCompliantCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "targetEnvironments": ["production"]
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetEnvironments", containsInAnyOrder("production")));

    var events = auditEventRepository.findByFlagKey("checkout-redesign");
    assertThat(events)
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED, AuditEventType.TARGET_ENVIRONMENTS_CHANGED);
    assertThat(events.get(1).details())
        .isEqualTo(
            new AuditEventDetails.TargetEnvironmentsChangedDetails(
                "targetEnvironments", Set.of("production", "staging"), Set.of("production")));
  }

  @Test
  void patchStatusRecordsEnabledAndDisabledEvents() throws Exception {
    createPolicyCompliantCheckoutFlag();

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
    createFlag("checkout-redesign", "ENABLED", Set.of("staging"), false, Set.of("tenant-a"), 100);

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
    createPolicyCompliantCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "status": "ENABLED",
                                  "killSwitchActive": true,
                                  "targetEnvironments": ["production", "staging"],
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
  void patchPolicyViolationReturnsUnprocessableContentAndDoesNotPersistOrAudit() throws Exception {
    createFlag("checkout-redesign", "ENABLED", Set.of("production"), false, Set.of(), 0);

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "rolloutPercentage": 100,
                                  "highRisk": true,
                                  "approvalGranted": false
                                }
                                """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"))
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(
            jsonPath(
                "$.violations[*].code",
                containsInAnyOrder(
                    "FULL_PRODUCTION_ROLLOUT",
                    "HIGH_RISK_REQUIRES_APPROVAL",
                    "PRODUCTION_ENABLEMENT_REQUIRES_REASON")));

    mockMvc
        .perform(get("/api/flags/checkout-redesign"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutPercentage").value(0))
        .andExpect(jsonPath("$.killSwitchActive").value(false))
        .andExpect(jsonPath("$.tenantAllowlist").isEmpty());

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED);
  }

  @Test
  void patchHighRiskUpdateSucceedsWhenApprovalGranted() throws Exception {
    createFlag("checkout-redesign", "ENABLED", Set.of("staging"), false, Set.of("tenant-a"), 25);

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "rolloutPercentage": 50,
                                  "highRisk": true,
                                  "approvalGranted": true
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutPercentage").value(50));

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED, AuditEventType.ROLLOUT_PERCENTAGE_CHANGED);
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
  void createWithOverlongFlagKeyReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "%s",
                                  "status": "ENABLED",
                                  "targetEnvironments": ["production"],
                                  "killSwitchActive": false,
                                  "rolloutPercentage": 25
                                }
                                """
                        .formatted(longString(201))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createWithOverlongTenantAllowlistEntryReturnsBadRequest() throws Exception {
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
                                  "tenantAllowlist": ["%s"],
                                  "rolloutPercentage": 25
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateWithOverlongTenantAllowlistEntryReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "tenantAllowlist": ["%s"]
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateWithOverlongReasonReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("rolloutPercentage", 50, "reason", longString(1001)))))
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
  void prometheusEndpointExposesFeatureFlagMetrics() throws Exception {
    createFlag("metrics-flag", "ENABLED", Set.of("staging"), false, Set.of("tenant-a"), 100);

    mockMvc
        .perform(
            post("/api/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "metrics-flag",
                                  "environment": "staging",
                                  "tenantId": "tenant-a"
                                }
                                """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/flags/metrics-flag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "killSwitchActive": true
                                }
                                """))
        .andExpect(status().isOk());

    MvcResult result =
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn();
    String metrics = result.getResponse().getContentAsString();

    assertPrometheusSample(
        metrics,
        "feature_flag_evaluation_total",
        1.0,
        "flag_key=\"metrics-flag\"",
        "environment=\"staging\"",
        "reason=\"TENANT_ALLOWLIST_MATCH\"");
    assertPrometheusSample(
        metrics,
        "feature_flag_evaluation_enabled_total",
        1.0,
        "flag_key=\"metrics-flag\"",
        "environment=\"staging\"",
        "reason=\"TENANT_ALLOWLIST_MATCH\"");
    assertPrometheusSample(metrics, "feature_flag_update_total", 1.0, "flag_key=\"metrics-flag\"");
    assertPrometheusSample(
        metrics, "feature_flag_kill_switch_enabled_total", 1.0, "flag_key=\"metrics-flag\"");
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

  @Test
  void evaluateWithOverlongIdentifiersReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "%s",
                                  "environment": "production"
                                }
                                """
                        .formatted(longString(201))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "environment": "%s"
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "environment": "production",
                                  "tenantId": "%s"
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "flagKey": "checkout-redesign",
                                  "environment": "production",
                                  "userId": "%s"
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewReturnsBeforeAfterDiffsAndSummary() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "killSwitchActive": true
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production",
                                      "tenantId": "tenant-a",
                                      "userId": "user-a"
                                    },
                                    {
                                      "environment": "production",
                                      "tenantId": "tenant-z",
                                      "userId": "user-z"
                                    }
                                  ]
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"))
        .andExpect(jsonPath("$.diffs[0].before.enabled").value(true))
        .andExpect(jsonPath("$.diffs[0].after.enabled").value(false))
        .andExpect(jsonPath("$.diffs[0].after.reason").value("KILL_SWITCH_ACTIVE"))
        .andExpect(jsonPath("$.diffs[0].changed").value(true))
        .andExpect(jsonPath("$.diffs[1].before.enabled").value(true))
        .andExpect(jsonPath("$.diffs[1].after.enabled").value(false))
        .andExpect(jsonPath("$.summary.sampleCount").value(2))
        .andExpect(jsonPath("$.summary.beforeEnabledCount").value(2))
        .andExpect(jsonPath("$.summary.beforeDisabledCount").value(0))
        .andExpect(jsonPath("$.summary.enabledCount").value(0))
        .andExpect(jsonPath("$.summary.disabledCount").value(2))
        .andExpect(jsonPath("$.summary.changedCount").value(2));
  }

  @Test
  void previewDoesNotPersistProposedChange() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "killSwitchActive": true,
                                    "rolloutPercentage": 0
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production",
                                      "tenantId": "tenant-z",
                                      "userId": "user-z"
                                    }
                                  ]
                                }
                                """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/flags/checkout-redesign"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.killSwitchActive").value(false))
        .andExpect(jsonPath("$.rolloutPercentage").value(100));

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED);
  }

  @Test
  void previewMissingFlagReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/flags/missing-flag/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production",
                                      "tenantId": "tenant-a"
                                    }
                                  ]
                                }
                                """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Feature flag not found"));
  }

  @Test
  void previewWithEmptySampleContextsReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": []
                                }
                                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewWithTooManySampleContextsReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": %s
                                }
                                """
                        .formatted(sampleContextsJson(101))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewWithInvalidSampleContextReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": " ",
                                      "tenantId": "tenant-a"
                                    }
                                  ]
                                }
                                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewWithBlankSampleContextOptionalIdentifiersReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production",
                                      "tenantId": " "
                                    }
                                  ]
                                }
                                """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production",
                                      "userId": " "
                                    }
                                  ]
                                }
                                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewWithOverlongPathFlagKeyReturnsBadRequest() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/flags/%s/preview".formatted(longString(201)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                    {
                                      "proposedChange": {
                                        "rolloutPercentage": 50
                                      },
                                      "sampleContexts": [
                                        {
                                          "environment": "production"
                                        }
                                      ]
                                    }
                                    """))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("preview.flagKey: size must be between 0 and 200");
  }

  @Test
  void previewWithOverlongSampleContextValuesReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "%s"
                                    }
                                  ]
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production",
                                      "tenantId": "%s"
                                    }
                                  ]
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production",
                                      "userId": "%s"
                                    }
                                  ]
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "tenantAllowlist": ["%s"]
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production"
                                    }
                                  ]
                                }
                                """
                        .formatted(longString(256))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewWithTooManyTenantAllowlistEntriesReturnsBadRequest() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "tenantAllowlist": %s
                                  },
                                  "sampleContexts": [
                                    {
                                      "environment": "production"
                                    }
                                  ]
                                }
                                """
                        .formatted(tenantAllowlistJson(1001))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validateChangeReturnsPolicyViolations() throws Exception {
    createFlag("checkout-redesign", "ENABLED", Set.of("production"), false, Set.of(), 0);

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/validate-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 100
                                  },
                                  "highRisk": true,
                                  "approvalGranted": false
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flagKey").value("checkout-redesign"))
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(
            jsonPath(
                "$.violations[*].code",
                containsInAnyOrder(
                    "FULL_PRODUCTION_ROLLOUT",
                    "HIGH_RISK_REQUIRES_APPROVAL",
                    "PRODUCTION_ENABLEMENT_REQUIRES_REASON")))
        .andExpect(
            jsonPath("$.violations[*].severity", containsInAnyOrder("ERROR", "ERROR", "ERROR")));
  }

  @Test
  void validateChangeMissingFlagReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/flags/missing-flag/validate-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 50
                                  }
                                }
                                """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Feature flag not found"));
  }

  @Test
  void validateChangeWithInvalidRequestFieldsReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/validate-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "rolloutPercentage": 101
                                  }
                                }
                                """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/validate-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": null
                                }
                                """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/validate-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "reason": "missing proposed change"
                                }
                                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validateChangeWithOverlongPathFlagKeyReturnsBadRequest() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/flags/%s/validate-change".formatted(longString(201)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                    {
                                      "proposedChange": {
                                        "rolloutPercentage": 50
                                      }
                                    }
                                    """))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("validateChange.flagKey: size must be between 0 and 200");
  }

  @Test
  void validateChangeWithOverlongReasonReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/validate-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "proposedChange",
                            Map.of("rolloutPercentage", 50),
                            "reason",
                            longString(1001)))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validateChangeDoesNotPersistProposedChangeOrWriteAuditEvents() throws Exception {
    createCheckoutFlag();

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/validate-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "proposedChange": {
                                    "targetEnvironments": ["staging"],
                                    "killSwitchActive": true,
                                    "rolloutPercentage": 0
                                  },
                                  "approvalGranted": true,
                                  "reason": "staged rollout validation"
                                }
                                """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/flags/checkout-redesign"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetEnvironments", containsInAnyOrder("production", "staging")))
        .andExpect(jsonPath("$.killSwitchActive").value(false))
        .andExpect(jsonPath("$.rolloutPercentage").value(100));

    assertThat(auditEventRepository.findByFlagKey("checkout-redesign"))
        .extracting(AuditEvent::eventType)
        .containsExactly(AuditEventType.FLAG_CREATED);
  }

  private static String sampleContextsJson(int count) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        json.append(",");
      }
      json.append(
          """
                      {
                        "environment": "production",
                        "tenantId": "tenant-%d"
                      }
                      """
              .formatted(i));
    }
    return json.append("]").toString();
  }

  private static String tenantAllowlistJson(int count) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        json.append(",");
      }
      json.append("\"tenant-").append(i).append("\"");
    }
    return json.append("]").toString();
  }

  private static String longString(int length) {
    return "a".repeat(length);
  }

  private static void assertPrometheusSample(
      String metrics, String metricName, double value, String... expectedLabels) {
    List<String> samples =
        metrics
            .lines()
            .filter(line -> !line.startsWith("#"))
            .filter(line -> line.startsWith(metricName + "{") || line.startsWith(metricName + " "))
            .toList();

    assertThat(samples)
        .anySatisfy(
            sample -> {
              assertThat(sample).contains(expectedLabels);
              assertThat(sample).endsWith(" " + value);
            });
  }

  /** Creates a checkout flag with production targeting and an inactive kill switch. */
  private void createCheckoutFlag() throws Exception {
    createFlag(
        "checkout-redesign",
        "ENABLED",
        Set.of("production", "staging"),
        false,
        Set.of("tenant-a"),
        100);
  }

  private void createPolicyCompliantCheckoutFlag() throws Exception {
    createFlag(
        "checkout-redesign",
        "ENABLED",
        Set.of("production", "staging"),
        true,
        Set.of("tenant-a"),
        100);
  }

  private void createFlag(
      String flagKey,
      String status,
      Set<String> targetEnvironments,
      boolean killSwitchActive,
      Set<String> tenantAllowlist,
      int rolloutPercentage)
      throws Exception {
    mockMvc
        .perform(
            post("/api/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "flagKey",
                            flagKey,
                            "status",
                            status,
                            "targetEnvironments",
                            targetEnvironments,
                            "killSwitchActive",
                            killSwitchActive,
                            "tenantAllowlist",
                            tenantAllowlist,
                            "rolloutPercentage",
                            rolloutPercentage))))
        .andExpect(status().isCreated());
  }
}

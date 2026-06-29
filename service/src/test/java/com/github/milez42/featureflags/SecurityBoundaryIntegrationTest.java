package com.github.milez42.featureflags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.milez42.featureflags.approval.ApprovalRequestResponse;
import com.github.milez42.featureflags.approval.RequestUpdateApprovalRequest;
import com.github.milez42.featureflags.approval.UpdateApprovalController;
import com.github.milez42.featureflags.approval.UpdateApprovalService;
import com.github.milez42.featureflags.approval.UpdateApprovalStatus;
import com.github.milez42.featureflags.flags.CreateFeatureFlagRequest;
import com.github.milez42.featureflags.flags.EvaluateFeatureFlagRequest;
import com.github.milez42.featureflags.flags.EvaluateFeatureFlagResponse;
import com.github.milez42.featureflags.flags.EvaluationReason;
import com.github.milez42.featureflags.flags.FeatureFlagController;
import com.github.milez42.featureflags.flags.FeatureFlagResponse;
import com.github.milez42.featureflags.flags.FeatureFlagService;
import com.github.milez42.featureflags.flags.FeatureFlagStatus;
import com.github.milez42.featureflags.flags.UpdateFeatureFlagRequest;
import com.github.milez42.featureflags.policy.RolloutPolicyController;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationRequest;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationResponse;
import com.github.milez42.featureflags.policy.RolloutPolicyValidationService;
import com.github.milez42.featureflags.preview.EvaluationPreviewController;
import com.github.milez42.featureflags.preview.EvaluationPreviewRequest;
import com.github.milez42.featureflags.preview.EvaluationPreviewResponse;
import com.github.milez42.featureflags.preview.EvaluationPreviewService;
import com.github.milez42.featureflags.preview.EvaluationPreviewSummary;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
  private static final String READER_USERNAME = "test-reader";
  private static final String READER_PASSWORD = "test-reader-password";
  private static final String OPERATOR_USERNAME = "test-operator";
  private static final String OPERATOR_PASSWORD = "test-operator-password";
  private static final String APPROVER_USERNAME = "test-approver";
  private static final String APPROVER_PASSWORD = "test-approver-password";

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private FeatureFlagService featureFlagService;

  @Autowired private EvaluationPreviewService evaluationPreviewService;

  @Autowired private RolloutPolicyValidationService rolloutPolicyValidationService;

  @Autowired private UpdateApprovalService updateApprovalService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(featureFlagService);
    reset(evaluationPreviewService);
    reset(rolloutPolicyValidationService);
    reset(updateApprovalService);
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void apiRequestsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/flags/checkout-redesign")).andExpect(status().isUnauthorized());
  }

  @Test
  void readerCanUseReadStyleApiRoutes() throws Exception {
    stubReadResponses();

    mockMvc
        .perform(get("/api/flags/checkout-redesign").with(readerCredentials()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/flags/checkout-redesign/audit-events").with(readerCredentials()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/evaluate")
                .with(readerCredentials())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "flagKey": "checkout-redesign",
                      "environment": "production"
                    }
                    """))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/preview")
                .with(readerCredentials())
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
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/validate-change")
                .with(readerCredentials())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "proposedChange": {
                        "rolloutPercentage": 50
                      }
                    }
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void readerCannotUseWriteApiRoutes() throws Exception {
    mockMvc
        .perform(
            post("/api/flags")
                .with(readerCredentials())
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
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .with(readerCredentials())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "rolloutPercentage": 50
                    }
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/approval-requests")
                .with(readerCredentials())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "rolloutPercentage": 80
                    }
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/approval-requests/%s/approve"
                    .formatted(approvalId()))
                .with(readerCredentials()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/flags/checkout-redesign/approval-requests/%s".formatted(approvalId()))
                .with(readerCredentials()))
        .andExpect(status().isForbidden());
  }

  @Test
  void operatorCanUseWriteApiRoutes() throws Exception {
    when(featureFlagService.create(any(CreateFeatureFlagRequest.class))).thenReturn(flagResponse());
    when(featureFlagService.update(any(String.class), any(UpdateFeatureFlagRequest.class)))
        .thenReturn(flagResponse());
    when(updateApprovalService.requestApproval(
            any(String.class), any(RequestUpdateApprovalRequest.class)))
        .thenReturn(approvalResponse(UpdateApprovalStatus.PENDING));
    when(updateApprovalService.get(any(String.class), any(UUID.class)))
        .thenReturn(approvalResponse(UpdateApprovalStatus.PENDING));

    mockMvc
        .perform(
            post("/api/flags")
                .with(operatorCredentials())
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

    mockMvc
        .perform(
            patch("/api/flags/checkout-redesign")
                .with(operatorCredentials())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "rolloutPercentage": 50
                    }
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/approval-requests")
                .with(operatorCredentials())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "rolloutPercentage": 80
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/flags/checkout-redesign/approval-requests/%s".formatted(approvalId()))
                .with(operatorCredentials()))
        .andExpect(status().isOk());
  }

  @Test
  void operatorCannotApproveOrRejectApprovalRequests() throws Exception {
    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/approval-requests/%s/approve"
                    .formatted(approvalId()))
                .with(operatorCredentials()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/approval-requests/%s/reject".formatted(approvalId()))
                .with(operatorCredentials()))
        .andExpect(status().isForbidden());
  }

  @Test
  void approverCanDecideAndReadApprovalRequests() throws Exception {
    when(updateApprovalService.approve(any(String.class), any(UUID.class)))
        .thenReturn(approvalResponse(UpdateApprovalStatus.APPROVED));
    when(updateApprovalService.reject(any(String.class), any(UUID.class)))
        .thenReturn(approvalResponse(UpdateApprovalStatus.REJECTED));
    when(updateApprovalService.get(any(String.class), any(UUID.class)))
        .thenReturn(approvalResponse(UpdateApprovalStatus.APPROVED));

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/approval-requests/%s/approve"
                    .formatted(approvalId()))
                .with(approverCredentials()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/flags/checkout-redesign/approval-requests/%s/reject".formatted(approvalId()))
                .with(approverCredentials()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/flags/checkout-redesign/approval-requests/%s".formatted(approvalId()))
                .with(approverCredentials()))
        .andExpect(status().isOk());
  }

  @Test
  void unclassifiedApiRoutesAreDeniedBeforeRequestDispatch() throws Exception {
    mockMvc
        .perform(get("/api/unclassified").with(operatorCredentials()))
        .andExpect(status().isForbidden());
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
        .perform(get("/actuator/prometheus").with(readerCredentials()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/actuator/prometheus").with(operatorCredentials()))
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
    mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/unlisted")).andExpect(status().isUnauthorized());
  }

  private void stubReadResponses() {
    when(featureFlagService.get("checkout-redesign")).thenReturn(flagResponse());
    when(featureFlagService.auditEvents("checkout-redesign")).thenReturn(List.of());
    when(featureFlagService.evaluate(any(EvaluateFeatureFlagRequest.class)))
        .thenReturn(
            new EvaluateFeatureFlagResponse(
                "checkout-redesign", true, EvaluationReason.ROLLOUT_MATCH, 42));
    when(evaluationPreviewService.preview(any(String.class), any(EvaluationPreviewRequest.class)))
        .thenReturn(
            new EvaluationPreviewResponse(
                "checkout-redesign", List.of(), new EvaluationPreviewSummary(0, 0, 0, 0, 0, 0)));
    when(rolloutPolicyValidationService.validate(
            any(String.class), any(RolloutPolicyValidationRequest.class)))
        .thenReturn(new RolloutPolicyValidationResponse("checkout-redesign", true, List.of()));
  }

  private FeatureFlagResponse flagResponse() {
    return new FeatureFlagResponse(
        "checkout-redesign", FeatureFlagStatus.ENABLED, Set.of("production"), false, Set.of(), 25);
  }

  private ApprovalRequestResponse approvalResponse(UpdateApprovalStatus status) {
    return new ApprovalRequestResponse(
        approvalId(),
        "checkout-redesign",
        OPERATOR_USERNAME,
        status == UpdateApprovalStatus.PENDING ? null : APPROVER_USERNAME,
        status,
        null,
        null,
        Set.of(),
        null,
        Instant.EPOCH,
        null,
        null);
  }

  private static UUID approvalId() {
    return UUID.fromString("5f0a5f6e-7f24-4f4f-a426-bb534ee726bd");
  }

  private static RequestPostProcessor readerCredentials() {
    return httpBasic(READER_USERNAME, READER_PASSWORD);
  }

  private static RequestPostProcessor operatorCredentials() {
    return httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD);
  }

  private static RequestPostProcessor approverCredentials() {
    return httpBasic(APPROVER_USERNAME, APPROVER_PASSWORD);
  }

  @Configuration
  @EnableAutoConfiguration
  @Import({
    FeatureFlagController.class,
    UpdateApprovalController.class,
    EvaluationPreviewController.class,
    RolloutPolicyController.class,
    OpenApiConfig.class,
    SecurityConfig.class
  })
  static class TestApplication {
    @Bean
    FeatureFlagService featureFlagService() {
      return mock(FeatureFlagService.class);
    }

    @Bean
    EvaluationPreviewService evaluationPreviewService() {
      return mock(EvaluationPreviewService.class);
    }

    @Bean
    RolloutPolicyValidationService rolloutPolicyValidationService() {
      return mock(RolloutPolicyValidationService.class);
    }

    @Bean
    UpdateApprovalService updateApprovalService() {
      return mock(UpdateApprovalService.class);
    }
  }
}

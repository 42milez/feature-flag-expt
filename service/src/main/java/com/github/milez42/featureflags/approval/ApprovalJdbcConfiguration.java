package com.github.milez42.featureflags.approval;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ApprovalJdbcConfiguration extends AbstractJdbcConfiguration {
  private final ObjectMapper objectMapper;

  public ApprovalJdbcConfiguration(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected List<?> userConverters() {
    return List.of(
        new FeatureFlagSnapshotReadingConverter(objectMapper),
        new FeatureFlagSnapshotWritingConverter(objectMapper),
        new RiskReasonSetReadingConverter(objectMapper),
        new RiskReasonSetWritingConverter(objectMapper));
  }
}

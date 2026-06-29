package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.flags.FeatureFlag;
import java.sql.JDBCType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@WritingConverter
public class FeatureFlagSnapshotWritingConverter implements Converter<FeatureFlag, JdbcValue> {
  private final ObjectMapper objectMapper;

  public FeatureFlagSnapshotWritingConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public JdbcValue convert(FeatureFlag source) {
    try {
      return JdbcValue.of(objectMapper.writeValueAsString(source), JDBCType.OTHER);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to serialize feature flag snapshot", ex);
    }
  }
}

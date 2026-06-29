package com.github.milez42.featureflags.approval;

import java.sql.JDBCType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@WritingConverter
public class RiskReasonSetWritingConverter implements Converter<RiskReasonSet, JdbcValue> {
  private final ObjectMapper objectMapper;

  public RiskReasonSetWritingConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public JdbcValue convert(RiskReasonSet source) {
    try {
      return JdbcValue.of(objectMapper.writeValueAsString(source.values()), JDBCType.OTHER);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to serialize approval risk reasons", ex);
    }
  }
}

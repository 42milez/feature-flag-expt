package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.policy.RiskReason;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ReadingConverter
public class RiskReasonSetReadingConverter implements Converter<Object, RiskReasonSet> {
  private final ObjectMapper objectMapper;

  public RiskReasonSetReadingConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public RiskReasonSet convert(Object source) {
    try {
      RiskReason[] reasons = objectMapper.readValue(source.toString(), RiskReason[].class);
      return new RiskReasonSet(Arrays.stream(reasons).collect(Collectors.toSet()));
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to deserialize approval risk reasons", ex);
    }
  }
}

package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.flags.FeatureFlag;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ReadingConverter
public class FeatureFlagSnapshotReadingConverter implements Converter<Object, FeatureFlag> {
  private final ObjectMapper objectMapper;

  public FeatureFlagSnapshotReadingConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public FeatureFlag convert(Object source) {
    try {
      return objectMapper.readValue(source.toString(), FeatureFlag.class);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to deserialize feature flag snapshot", ex);
    }
  }
}

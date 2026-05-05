package com.github.milez42.featureflags.flags;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

// TODO: Replace with a database-managed environments table to allow runtime configuration without
// redeployment.
public enum Environment {
  DEVELOPMENT,
  STAGING,
  PRODUCTION;

  @JsonValue
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static Environment fromValue(String value) {
    for (Environment env : values()) {
      if (env.value().equalsIgnoreCase(value)) {
        return env;
      }
    }
    throw new IllegalArgumentException("Unknown environment: " + value);
  }
}

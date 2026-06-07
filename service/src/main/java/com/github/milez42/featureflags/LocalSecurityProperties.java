package com.github.milez42.featureflags;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("feature-flags.security")
@Validated
public record LocalSecurityProperties(
    @NotBlank String readerUsername,
    @NotBlank String readerPassword,
    @NotBlank String operatorUsername,
    @NotBlank String operatorPassword) {
  @AssertTrue(message = "readerUsername and operatorUsername must be different")
  public boolean isDistinctUsernames() {
    return !readerUsername.equals(operatorUsername);
  }
}

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
    @NotBlank String operatorPassword,
    @NotBlank String approverUsername,
    @NotBlank String approverPassword) {
  @AssertTrue(message = "readerUsername, operatorUsername, and approverUsername must be different")
  public boolean isDistinctUsernames() {
    return !readerUsername.equals(operatorUsername)
        && !readerUsername.equals(approverUsername)
        && !operatorUsername.equals(approverUsername);
  }
}

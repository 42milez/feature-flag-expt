package com.github.milez42.featureflags.flags;

import com.github.milez42.featureflags.error.HttpException;
import org.springframework.http.HttpStatus;

public class FeatureFlagNotFoundException extends HttpException {
  public FeatureFlagNotFoundException(String flagKey) {
    super("Feature flag not found: " + flagKey);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String title() {
    return "Feature flag not found";
  }
}

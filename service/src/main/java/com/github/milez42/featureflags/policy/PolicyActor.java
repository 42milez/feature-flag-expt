package com.github.milez42.featureflags.policy;

import java.util.Objects;
import java.util.Set;

public record PolicyActor(String username, Set<String> roles) {
  public PolicyActor {
    Objects.requireNonNull(username, "username must not be null");
    Objects.requireNonNull(roles, "roles must not be null");
    if (username.isBlank()) {
      throw new IllegalArgumentException("username must not be blank");
    }
    roles = Set.copyOf(roles);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }
}

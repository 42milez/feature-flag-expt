package com.github.milez42.featureflags.audit;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorProvider {
  public String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      throw new IllegalStateException("No trusted authenticated actor is available");
    }

    String actor = authentication.getName();
    // Keep the literal guard as a fallback for custom anonymous tokens that do not use
    // AnonymousAuthenticationToken.
    if (actor == null || actor.isBlank() || "anonymousUser".equals(actor)) {
      throw new IllegalStateException("No trusted authenticated actor is available");
    }

    return actor;
  }
}

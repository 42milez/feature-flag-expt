package com.github.milez42.featureflags.audit;

import com.github.milez42.featureflags.policy.PolicyActor;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorProvider {
  public String currentActor() {
    return currentActor(currentAuthentication());
  }

  public PolicyActor currentPolicyActor() {
    Authentication authentication = currentAuthentication();
    return new PolicyActor(
        currentActor(authentication),
        authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
            .collect(Collectors.toUnmodifiableSet()));
  }

  private Authentication currentAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      throw new IllegalStateException("No trusted authenticated actor is available");
    }
    return authentication;
  }

  private String currentActor(Authentication authentication) {
    String actor = authentication.getName();
    // Keep the literal guard as a fallback for custom anonymous tokens that do not use
    // AnonymousAuthenticationToken.
    if (actor == null || actor.isBlank() || "anonymousUser".equals(actor)) {
      throw new IllegalStateException("No trusted authenticated actor is available");
    }

    return actor;
  }
}

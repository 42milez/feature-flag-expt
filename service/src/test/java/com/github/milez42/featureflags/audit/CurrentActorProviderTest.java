package com.github.milez42.featureflags.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentActorProviderTest {
  private final CurrentActorProvider provider = new CurrentActorProvider();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsAuthenticatedUsername() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "test-operator",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_FLAG_OPERATOR"))));

    assertThat(provider.currentActor()).isEqualTo("test-operator");
  }

  @Test
  void throwsForMissingAuthentication() {
    assertThatThrownBy(provider::currentActor)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No trusted authenticated actor is available");
  }

  @Test
  void throwsForUnauthenticatedPrincipal() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.unauthenticated("test-operator", "password"));

    assertThatThrownBy(provider::currentActor).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsForBlankPrincipalName() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                " ", "password", List.of(new SimpleGrantedAuthority("ROLE_FLAG_OPERATOR"))));

    assertThatThrownBy(provider::currentActor).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsForAnonymousAuthenticationToken() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertThatThrownBy(provider::currentActor).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsForAnonymousUserName() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "anonymousUser",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_FLAG_OPERATOR"))));

    assertThatThrownBy(provider::currentActor).isInstanceOf(IllegalStateException.class);
  }
}

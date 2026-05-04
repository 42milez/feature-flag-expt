package com.github.milez42.featureflags.flags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagEvaluator {
  public EvaluationResult evaluate(FeatureFlag flag, EvaluationContext context) {
    Objects.requireNonNull(flag, "flag must not be null");
    Objects.requireNonNull(context, "context must not be null");

    if (flag.status() == FeatureFlagStatus.DISABLED) {
      return new EvaluationResult(false, EvaluationReason.FLAG_DISABLED, null);
    }
    if (!flag.targetEnvironments().contains(context.environment())) {
      return new EvaluationResult(false, EvaluationReason.ENVIRONMENT_NOT_TARGETED, null);
    }
    if (flag.killSwitchActive()) {
      return new EvaluationResult(false, EvaluationReason.KILL_SWITCH_ACTIVE, null);
    }
    if (context.tenantId() != null && flag.tenantAllowlist().contains(context.tenantId())) {
      return new EvaluationResult(true, EvaluationReason.TENANT_ALLOWLIST_MATCH, null);
    }

    String rolloutIdentity = rolloutIdentity(context);
    if (rolloutIdentity == null) {
      return new EvaluationResult(false, EvaluationReason.ROLLOUT_MISS, null);
    }

    int bucket = bucket(flag.flagKey(), rolloutIdentity);
    if (bucket < flag.rolloutPercentage()) {
      return new EvaluationResult(true, EvaluationReason.ROLLOUT_MATCH, bucket);
    }
    return new EvaluationResult(false, EvaluationReason.ROLLOUT_MISS, bucket);
  }

  private String rolloutIdentity(EvaluationContext context) {
    if (hasText(context.tenantId())) {
      return context.tenantId();
    }
    if (hasText(context.userId())) {
      return context.userId();
    }
    return null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private int bucket(String flagKey, String rolloutIdentity) {
    byte[] digest = sha256(flagKey + ":" + rolloutIdentity);
    int value = ByteBuffer.wrap(digest).getInt();
    return Math.floorMod(value, 100);
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}

package com.github.milez42.featureflags.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AuditEventRepository {
  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public AuditEventRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public void save(AuditEvent event) {
    String detailsJson = serialize(event.details());
    jdbcClient
        .sql(
            """
            insert into audit_events (flag_key, event_type, actor, details, occurred_at)
            values (:flagKey, :eventType, :actor, cast(:details as jsonb), :occurredAt)
            """)
        .param("flagKey", event.flagKey())
        .param("eventType", event.eventType().name())
        .param("actor", event.actor())
        .param("details", detailsJson)
        .param("occurredAt", event.occurredAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  public List<AuditEvent> findByFlagKey(String flagKey) {
    return jdbcClient
        .sql(
            """
            select id, flag_key, event_type, actor, details::text as details, occurred_at
            from audit_events
            where flag_key = :flagKey
            order by id
            """)
        .param("flagKey", flagKey)
        .query(this::mapEvent)
        .list();
  }

  private AuditEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
    AuditEventType eventType = AuditEventType.valueOf(rs.getString("event_type"));
    return new AuditEvent(
        rs.getLong("id"),
        rs.getString("flag_key"),
        eventType,
        rs.getString("actor"),
        deserialize(eventType, rs.getString("details")),
        rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
  }

  private String serialize(AuditEventDetails details) {
    try {
      return objectMapper.writeValueAsString(details);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to serialize audit event details", ex);
    }
  }

  private AuditEventDetails deserialize(AuditEventType eventType, String detailsJson) {
    try {
      return switch (eventType) {
        case FLAG_CREATED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.FlagCreatedDetails.class);
        case FLAG_ENABLED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.FlagEnabledDetails.class);
        case FLAG_DISABLED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.FlagDisabledDetails.class);
        case ROLLOUT_PERCENTAGE_CHANGED ->
            objectMapper.readValue(
                detailsJson, AuditEventDetails.RolloutPercentageChangedDetails.class);
        case TARGET_ENVIRONMENTS_CHANGED ->
            objectMapper.readValue(
                detailsJson, AuditEventDetails.TargetEnvironmentsChangedDetails.class);
        case TENANT_ALLOWLIST_CHANGED ->
            objectMapper.readValue(
                detailsJson, AuditEventDetails.TenantAllowlistChangedDetails.class);
        case KILL_SWITCH_ENABLED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.KillSwitchEnabledDetails.class);
        case KILL_SWITCH_DISABLED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.KillSwitchDisabledDetails.class);
        case APPROVAL_REQUESTED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.ApprovalRequestedDetails.class);
        case APPROVAL_APPROVED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.ApprovalApprovedDetails.class);
        case APPROVAL_REJECTED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.ApprovalRejectedDetails.class);
        case APPROVAL_USED ->
            objectMapper.readValue(detailsJson, AuditEventDetails.ApprovalUsedDetails.class);
      };
    } catch (JacksonException ex) {
      throw new IllegalStateException("Failed to deserialize audit event details", ex);
    }
  }
}

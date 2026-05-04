package com.github.milez42.featureflags.flags;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("feature_flag_target_environments")
public record TargetEnvironmentEntity(@Column("environment") String environment) {}

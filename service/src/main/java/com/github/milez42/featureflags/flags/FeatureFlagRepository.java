package com.github.milez42.featureflags.flags;

import org.springframework.data.repository.CrudRepository;

public interface FeatureFlagRepository extends CrudRepository<FeatureFlagEntity, String> {
}

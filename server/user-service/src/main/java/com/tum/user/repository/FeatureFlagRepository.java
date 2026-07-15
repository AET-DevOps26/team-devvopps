package com.tum.user.repository;

import com.tum.user.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for runtime feature flags. */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {
}

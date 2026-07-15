package com.tum.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A runtime feature toggle. Stored in the database so admins can flip
 * features from the admin panel without a redeploy, and the state survives
 * pod restarts. Defaults are seeded on startup by {@link com.tum.user.security.FeatureFlagBootstrap}.
 */
@Entity
@Table(name = "feature_flags")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlag {

    /** Flag identifier, e.g. "tokenQuota" — referenced by clients and services. */
    @Id
    private String name;

    /** Whether the feature is currently enabled. */
    @Column(nullable = false)
    private boolean enabled;

    /** Human-readable explanation shown in the admin panel. */
    @Column(nullable = false)
    private String description;
}

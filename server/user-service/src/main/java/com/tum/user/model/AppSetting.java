package com.tum.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A runtime text setting (prompt sections, numeric limits). Editable from
 * the admin panel without a redeploy; survives pod restarts. Defaults are
 * seeded on startup by {@link com.tum.user.security.SettingBootstrap}.
 */
@Entity
@Table(name = "app_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppSetting {

    /** Setting identifier, e.g. "promptInstructions". */
    @Id
    private String name;

    /** The setting's value — free text, may be multi-line. */
    @Lob
    @Column(nullable = false)
    private String value;

    /** Human-readable explanation shown in the admin panel. */
    @Column(nullable = false)
    private String description;
}

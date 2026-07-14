package com.tum.user.security;

import com.tum.user.model.FeatureFlag;
import com.tum.user.repository.FeatureFlagRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup, seeds the known feature flags with their default state.
 * Idempotent: existing flags keep whatever state an admin last set —
 * only missing flags are inserted.
 */
@Component
public class FeatureFlagBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagBootstrap.class);

    private final FeatureFlagRepository repo;

    public FeatureFlagBootstrap(FeatureFlagRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        List<FeatureFlag> defaults = List.of(
                new FeatureFlag("tokenQuota", true,
                        "Enforce the monthly per-user LLM token quota"),
                new FeatureFlag("llmLogs", true,
                        "Expose LLM request logs in the admin panel"),
                new FeatureFlag("grafanaLink", true,
                        "Show the Grafana monitoring link in the admin panel"),
                new FeatureFlag("llmUseLogos", true,
                        "Use TUM Logos (gpt-oss-120b) as the LLM provider — OFF falls back to Groq (much faster)")
        );
        for (FeatureFlag flag : defaults) {
            if (repo.findById(flag.getName()).isEmpty()) {
                repo.save(flag);
                log.info("[Features] seeded flag '{}' (enabled={})", flag.getName(), flag.isEnabled());
            }
        }
    }
}

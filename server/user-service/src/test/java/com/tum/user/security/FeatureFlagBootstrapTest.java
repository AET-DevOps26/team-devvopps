package com.tum.user.security;

import com.tum.user.model.FeatureFlag;
import com.tum.user.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;


import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeatureFlagBootstrap.
 *
 * Verifies the seeding logic executed at startup. The repository is mocked
 * so tests are fully isolated from the database.
 *
 * The tests cover:
 * - All four default flags are saved when none exist yet.
 * - Each flag is seeded with the correct name, enabled state, and description.
 * - A flag that already exists in the repository is not overwritten.
 * - Only missing flags are inserted when the repository is partially seeded.
 * - No repository writes occur when all flags are already present.
 */

@ExtendWith(MockitoExtension.class)
class FeatureFlagBootstrapTest {

    @Mock
    private FeatureFlagRepository repo;

    private FeatureFlagBootstrap bootstrap;

    /**
     * Creates a fresh FeatureFlagBootstrap with the mocked repository
     * before each test. The bootstrap is instantiated directly (not via Spring)
     * so the suite stays lightweight and fast.
     */
    @BeforeEach
    void setUp() {
        bootstrap = new FeatureFlagBootstrap(repo);
    }


    // -------------------------------------------------------------------------
    // All flags missing -> full seed
    // -------------------------------------------------------------------------

    /**
     * Verifies that all four default flags are persisted when the repository
     * is empty (first-time startup).
     */
    @Test
    @DisplayName("Should seed all four flags when none exist")
    void shouldSeedAllFlagsWhenNoneExist() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        bootstrap.run();

        verify(repo, times(4)).save(any(FeatureFlag.class));
    }

    /**
     * Verifies that the "tokenQuota" flag is seeded with enabled=true.
     */
    @Test
    @DisplayName("Should seed 'tokenQuota' as enabled")
    void shouldSeedTokenQuotaAsEnabled() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        FeatureFlag tokenQuota = captor.getAllValues().stream()
                .filter(f -> "tokenQuota".equals(f.getName()))
                .findFirst()
                .orElseThrow();

        assertTrue(tokenQuota.isEnabled());
        assertNotNull(tokenQuota.getDescription());
    }

    /**
     * Verifies that the "llmLogs" flag is seeded with enabled=true.
     */
    @Test
    @DisplayName("Should seed 'llmLogs' as enabled")
    void shouldSeedLlmLogsAsEnabled() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        FeatureFlag llmLogs = captor.getAllValues().stream()
                .filter(f -> "llmLogs".equals(f.getName()))
                .findFirst()
                .orElseThrow();

        assertTrue(llmLogs.isEnabled());
        assertNotNull(llmLogs.getDescription());
    }

    /**
     * Verifies that the "grafanaLink" flag is seeded with enabled=true.
     */
    @Test
    @DisplayName("Should seed 'grafanaLink' as enabled")
    void shouldSeedGrafanaLinkAsEnabled() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        FeatureFlag grafanaLink = captor.getAllValues().stream()
                .filter(f -> "grafanaLink".equals(f.getName()))
                .findFirst()
                .orElseThrow();

        assertTrue(grafanaLink.isEnabled());
        assertNotNull(grafanaLink.getDescription());
    }

    /**
     * Verifies that the "llmUseLogos" flag is seeded with enabled=true.
     */
    @Test
    @DisplayName("Should seed 'llmUseLogos' as enabled")
    void shouldSeedLlmUseLogosAsEnabled() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        FeatureFlag llmUseLogos = captor.getAllValues().stream()
                .filter(f -> "llmUseLogos".equals(f.getName()))
                .findFirst()
                .orElseThrow();

        assertTrue(llmUseLogos.isEnabled());
        assertNotNull(llmUseLogos.getDescription());
    }


    // -------------------------------------------------------------------------
    // Idempotency – existing flags must not be overwritten
    // -------------------------------------------------------------------------

    /**
     * Verifies that a flag already present in the repository is skipped
     * and not overwritten by the bootstrap.
     */
    @Test
    @DisplayName("Should not overwrite a flag that already exists")
    void shouldNotOverwriteExistingFlag() throws Exception {

        FeatureFlag existing = new FeatureFlag("tokenQuota", false, "existing description");

        when(repo.findById("tokenQuota")).thenReturn(Optional.of(existing));
        when(repo.findById(argThat(id -> !"tokenQuota".equals(id)))).thenReturn(Optional.empty());

        bootstrap.run();

        // Only the 3 missing flags should be saved; tokenQuota must be skipped.
        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        verify(repo, times(3)).save(captor.capture());

        boolean tokenQuotaSaved = captor.getAllValues().stream()
                .anyMatch(f -> "tokenQuota".equals(f.getName()));

        assertFalse(tokenQuotaSaved, "tokenQuota should not have been overwritten");
    }

    /**
     * Verifies that only missing flags are inserted when the repository
     * is partially seeded (some flags already exist).
     */
    @Test
    @DisplayName("Should only seed missing flags when repository is partially seeded")
    void shouldOnlySeedMissingFlags() throws Exception {

        when(repo.findById("tokenQuota")).thenReturn(
                Optional.of(new FeatureFlag("tokenQuota", true, "existing")));
        when(repo.findById("llmLogs")).thenReturn(
                Optional.of(new FeatureFlag("llmLogs", true, "existing")));
        when(repo.findById("grafanaLink")).thenReturn(Optional.empty());
        when(repo.findById("llmUseLogos")).thenReturn(Optional.empty());

        bootstrap.run();

        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        verify(repo, times(2)).save(captor.capture());

        assertTrue(captor.getAllValues().stream().anyMatch(f -> "grafanaLink".equals(f.getName())));
        assertTrue(captor.getAllValues().stream().anyMatch(f -> "llmUseLogos".equals(f.getName())));
    }

    /**
     * Verifies that no repository writes occur when all flags are already
     * present (e.g. subsequent application restarts).
     */
    @Test
    @DisplayName("Should not save any flags when all already exist")
    void shouldNotSaveAnyFlagWhenAllExist() throws Exception {

        when(repo.findById("tokenQuota")).thenReturn(
                Optional.of(new FeatureFlag("tokenQuota", true, "existing")));
        when(repo.findById("llmLogs")).thenReturn(
                Optional.of(new FeatureFlag("llmLogs", true, "existing")));
        when(repo.findById("grafanaLink")).thenReturn(
                Optional.of(new FeatureFlag("grafanaLink", true, "existing")));
        when(repo.findById("llmUseLogos")).thenReturn(
                Optional.of(new FeatureFlag("llmUseLogos", true, "existing")));

        bootstrap.run();

        verify(repo, never()).save(any());
    }
}
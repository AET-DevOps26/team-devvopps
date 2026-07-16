package com.tum.user.security;

import com.tum.user.model.AppSetting;
import com.tum.user.repository.AppSettingRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SettingBootstrap.
 *
 * Verifies the seeding logic executed at startup. The repository is mocked
 * so tests are fully isolated from the database.
 *
 * The tests cover:
 * - All four default settings are saved when none exist yet.
 * - Each setting is seeded with the correct name, value, and description.
 * - The default values match the constants defined in SettingBootstrap.
 * - A setting that already exists in the repository is not overwritten.
 * - Only missing settings are inserted when the repository is partially seeded.
 * - No repository writes occur when all settings are already present.
 */

@ExtendWith(MockitoExtension.class)
class SettingBootstrapTest {

    @Mock
    private AppSettingRepository repo;

    private SettingBootstrap bootstrap;

    /**
     * Creates a fresh SettingBootstrap with the mocked repository
     * before each test. The bootstrap is instantiated directly (not via Spring)
     * so the suite stays lightweight and fast.
     */
    @BeforeEach
    void setUp() {
        bootstrap = new SettingBootstrap(repo);
    }


    // -------------------------------------------------------------------------
    // All settings missing -> full seed
    // -------------------------------------------------------------------------

    /**
     * Verifies that all four default settings are persisted when the
     * repository is empty (first-time startup).
     */
    @Test
    @DisplayName("Should seed all four settings when none exist")
    void shouldSeedAllSettingsWhenNoneExist() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        bootstrap.run();

        verify(repo, times(4)).save(any(AppSetting.class));
    }

    /**
     * Verifies that "promptRole" is seeded with the correct default value
     * and a non-null description.
     */
    @Test
    @DisplayName("Should seed 'promptRole' with the correct default value")
    void shouldSeedPromptRoleWithCorrectValue() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        AppSetting promptRole = captor.getAllValues().stream()
                .filter(s -> "promptRole".equals(s.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(SettingBootstrap.DEFAULT_ROLE, promptRole.getValue());
        assertNotNull(promptRole.getDescription());
    }

    /**
     * Verifies that "promptInstructions" is seeded with the correct default
     * value and a non-null description.
     */
    @Test
    @DisplayName("Should seed 'promptInstructions' with the correct default value")
    void shouldSeedPromptInstructionsWithCorrectValue() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        AppSetting promptInstructions = captor.getAllValues().stream()
                .filter(s -> "promptInstructions".equals(s.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(SettingBootstrap.DEFAULT_INSTRUCTIONS, promptInstructions.getValue());
        assertNotNull(promptInstructions.getDescription());
    }

    /**
     * Verifies that "promptResponseFormat" is seeded with the correct default
     * value and a non-null description.
     */
    @Test
    @DisplayName("Should seed 'promptResponseFormat' with the correct default value")
    void shouldSeedPromptResponseFormatWithCorrectValue() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        AppSetting promptResponseFormat = captor.getAllValues().stream()
                .filter(s -> "promptResponseFormat".equals(s.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(SettingBootstrap.DEFAULT_RESPONSE_FORMAT, promptResponseFormat.getValue());
        assertNotNull(promptResponseFormat.getDescription());
    }

    /**
     * Verifies that "monthlyTokenLimit" is seeded with the value "50000"
     * and a non-null description.
     */
    @Test
    @DisplayName("Should seed 'monthlyTokenLimit' with value '50000'")
    void shouldSeedMonthlyTokenLimitWithCorrectValue() throws Exception {

        when(repo.findById(any())).thenReturn(Optional.empty());

        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        bootstrap.run();
        verify(repo, times(4)).save(captor.capture());

        AppSetting monthlyTokenLimit = captor.getAllValues().stream()
                .filter(s -> "monthlyTokenLimit".equals(s.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals("50000", monthlyTokenLimit.getValue());
        assertNotNull(monthlyTokenLimit.getDescription());
    }


    // -------------------------------------------------------------------------
    // Idempotency – existing settings must not be overwritten
    // -------------------------------------------------------------------------

    /**
     * Verifies that a setting already present in the repository is skipped
     * and not overwritten by the bootstrap.
     */
    @Test
    @DisplayName("Should not overwrite a setting that already exists")
    void shouldNotOverwriteExistingSetting() throws Exception {

        AppSetting existing = new AppSetting("promptRole", "custom value", "custom description");

        when(repo.findById("promptRole")).thenReturn(Optional.of(existing));
        when(repo.findById(argThat(id -> !"promptRole".equals(id)))).thenReturn(Optional.empty());

        bootstrap.run();

        // Only the 3 missing settings should be saved; promptRole must be skipped.
        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(repo, times(3)).save(captor.capture());

        boolean promptRoleSaved = captor.getAllValues().stream()
                .anyMatch(s -> "promptRole".equals(s.getName()));

        assertFalse(promptRoleSaved, "promptRole should not have been overwritten");
    }

    /**
     * Verifies that only missing settings are inserted when the repository
     * is partially seeded (some settings already exist).
     */
    @Test
    @DisplayName("Should only seed missing settings when repository is partially seeded")
    void shouldOnlySeedMissingSettings() throws Exception {

        when(repo.findById("promptRole")).thenReturn(
                Optional.of(new AppSetting("promptRole", "custom", "existing")));
        when(repo.findById("promptInstructions")).thenReturn(
                Optional.of(new AppSetting("promptInstructions", "custom", "existing")));
        when(repo.findById("promptResponseFormat")).thenReturn(Optional.empty());
        when(repo.findById("monthlyTokenLimit")).thenReturn(Optional.empty());

        bootstrap.run();

        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(repo, times(2)).save(captor.capture());

        assertTrue(captor.getAllValues().stream().anyMatch(s -> "promptResponseFormat".equals(s.getName())));
        assertTrue(captor.getAllValues().stream().anyMatch(s -> "monthlyTokenLimit".equals(s.getName())));
    }

    /**
     * Verifies that no repository writes occur when all settings are already
     * present (e.g. subsequent application restarts).
     */
    @Test
    @DisplayName("Should not save any settings when all already exist")
    void shouldNotSaveAnySettingWhenAllExist() throws Exception {

        when(repo.findById("promptRole")).thenReturn(
                Optional.of(new AppSetting("promptRole", "custom", "existing")));
        when(repo.findById("promptInstructions")).thenReturn(
                Optional.of(new AppSetting("promptInstructions", "custom", "existing")));
        when(repo.findById("promptResponseFormat")).thenReturn(
                Optional.of(new AppSetting("promptResponseFormat", "custom", "existing")));
        when(repo.findById("monthlyTokenLimit")).thenReturn(
                Optional.of(new AppSetting("monthlyTokenLimit", "50000", "existing")));

        bootstrap.run();

        verify(repo, never()).save(any());
    }
}
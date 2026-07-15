package com.tum.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tum.user.model.AppSetting;
import com.tum.user.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for SettingController.
 *
 * Uses @WebMvcTest to load only the web layer without a full
 * application context. AppSettingRepository is mocked so tests
 * are fully isolated from the database.
 *
 * The tests cover:
 * - GET /settings returns all settings.
 * - GET /settings returns an empty list when no settings exist.
 * - PUT /settings/{name} updates an existing setting successfully.
 * - PUT /settings/{name} returns 404 for an unknown setting name.
 * - PUT /settings/{name} returns 400 when the "value" field is missing.
 * - PUT /settings/{name} returns 400 when the "value" field is blank.
 * - PUT /settings/{name} returns 400 when the body is empty.
 * - PUT /settings/monthlyTokenLimit returns 400 for a non-numeric value.
 * - PUT /settings/monthlyTokenLimit returns 400 for zero.
 * - PUT /settings/monthlyTokenLimit returns 400 for a negative integer.
 * - PUT /settings/monthlyTokenLimit updates successfully for a positive integer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AppSettingRepository repo;

    private AppSetting systemPrompt;
    private AppSetting tokenLimit;

    /**
     * Sets up reusable AppSetting fixtures before each test.
     * Repository stubs are defined per-test to keep each case self-contained.
     */
    @BeforeEach
    void setUp() {
        // Reset the mock before each test to clear any save() calls made by
        // SettingBootstrap at application startup, which would otherwise
        // cause verify(repo).save(...) to count bootstrap invocations too.
        Mockito.reset(repo);

        systemPrompt = new AppSetting("systemPrompt", "You are a helpful assistant.", "System prompt");
        tokenLimit   = new AppSetting("monthlyTokenLimit", "10000", "Monthly token limit");
    }


    // -------------------------------------------------------------------------
    // GET /settings
    // -------------------------------------------------------------------------

    /**
     * Verifies that GET /settings returns all settings as JSON
     * with HTTP 200.
     */
    @Test
    @DisplayName("GET /settings - should return all settings")
    void shouldReturnAllSettings() throws Exception {

        when(repo.findAll()).thenReturn(List.of(systemPrompt, tokenLimit));

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("systemPrompt"))
                .andExpect(jsonPath("$[0].value").value("You are a helpful assistant."))
                .andExpect(jsonPath("$[1].name").value("monthlyTokenLimit"))
                .andExpect(jsonPath("$[1].value").value("10000"));
    }

    /**
     * Verifies that GET /settings returns an empty JSON array
     * when no settings are seeded.
     */
    @Test
    @DisplayName("GET /settings - should return empty list when no settings exist")
    void shouldReturnEmptyListWhenNoSettingsExist() throws Exception {

        when(repo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }


    // -------------------------------------------------------------------------
    // PUT /settings/{name} – general settings
    // -------------------------------------------------------------------------

    /**
     * Verifies that PUT /settings/{name} with a valid body updates
     * the setting and returns the saved resource with HTTP 200.
     */
    @Test
    @DisplayName("PUT /settings/{name} - should update an existing setting")
    void shouldUpdateExistingSetting() throws Exception {

        AppSetting updated = new AppSetting("systemPrompt", "You are a concise assistant.", "System prompt");

        when(repo.findById("systemPrompt")).thenReturn(Optional.of(systemPrompt));
        when(repo.save(any(AppSetting.class))).thenReturn(updated);

        mockMvc.perform(put("/settings/systemPrompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "You are a concise assistant."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("systemPrompt"))
                .andExpect(jsonPath("$.value").value("You are a concise assistant."));

        verify(repo).save(any(AppSetting.class));
    }

    /**
     * Verifies that PUT /settings/{name} returns HTTP 404
     * when the setting name does not exist in the repository.
     */
    @Test
    @DisplayName("PUT /settings/{name} - should return 404 for unknown setting")
    void shouldReturn404ForUnknownSetting() throws Exception {

        when(repo.findById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(put("/settings/non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "something"))))
                .andExpect(status().isNotFound());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /settings/{name} returns HTTP 400
     * when the request body is present but the "value" key is missing.
     */
    @Test
    @DisplayName("PUT /settings/{name} - should return 400 when 'value' field is missing")
    void shouldReturn400WhenValueFieldIsMissing() throws Exception {

        mockMvc.perform(put("/settings/systemPrompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("foo", "bar"))))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /settings/{name} returns HTTP 400
     * when the "value" field is present but blank.
     */
    @Test
    @DisplayName("PUT /settings/{name} - should return 400 when 'value' is blank")
    void shouldReturn400WhenValueIsBlank() throws Exception {

        mockMvc.perform(put("/settings/systemPrompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "   "))))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /settings/{name} returns HTTP 400
     * when the request body is completely empty.
     */
    @Test
    @DisplayName("PUT /settings/{name} - should return 400 when body is empty")
    void shouldReturn400WhenBodyIsEmpty() throws Exception {

        mockMvc.perform(put("/settings/systemPrompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }


    // -------------------------------------------------------------------------
    // PUT /settings/monthlyTokenLimit – extra numeric validation
    // -------------------------------------------------------------------------

    /**
     * Verifies that PUT /settings/monthlyTokenLimit returns HTTP 400
     * when the value is not a number.
     */
    @Test
    @DisplayName("PUT /settings/monthlyTokenLimit - should return 400 for non-numeric value")
    void shouldReturn400ForNonNumericTokenLimit() throws Exception {

        mockMvc.perform(put("/settings/monthlyTokenLimit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "not-a-number"))))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /settings/monthlyTokenLimit returns HTTP 400
     * when the value is zero (must be strictly positive).
     */
    @Test
    @DisplayName("PUT /settings/monthlyTokenLimit - should return 400 for zero")
    void shouldReturn400ForZeroTokenLimit() throws Exception {

        mockMvc.perform(put("/settings/monthlyTokenLimit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "0"))))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /settings/monthlyTokenLimit returns HTTP 400
     * when the value is a negative integer.
     */
    @Test
    @DisplayName("PUT /settings/monthlyTokenLimit - should return 400 for negative value")
    void shouldReturn400ForNegativeTokenLimit() throws Exception {

        mockMvc.perform(put("/settings/monthlyTokenLimit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "-500"))))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /settings/monthlyTokenLimit accepts a positive integer
     * and returns the updated setting with HTTP 200.
     */
    @Test
    @DisplayName("PUT /settings/monthlyTokenLimit - should update successfully for positive integer")
    void shouldUpdateTokenLimitSuccessfully() throws Exception {

        AppSetting updated = new AppSetting("monthlyTokenLimit", "20000", "Monthly token limit");

        when(repo.findById("monthlyTokenLimit")).thenReturn(Optional.of(tokenLimit));
        when(repo.save(any(AppSetting.class))).thenReturn(updated);

        mockMvc.perform(put("/settings/monthlyTokenLimit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "20000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("monthlyTokenLimit"))
                .andExpect(jsonPath("$.value").value("20000"));

        verify(repo).save(any(AppSetting.class));
    }
}
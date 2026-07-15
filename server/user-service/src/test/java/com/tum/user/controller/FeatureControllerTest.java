package com.tum.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tum.user.model.FeatureFlag;
import com.tum.user.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;


import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for FeatureController.
 *
 * Uses @WebMvcTest to load only the web layer (controller +
 * MockMvc) without a full application context, keeping the suite fast.
 * FeatureFlagRepository is mocked so tests are fully isolated
 * from the database.
 *
 * The tests cover:
 * - GET /features returns all flags.
 * - GET /features returns an empty list when no flags exist.
 * - PUT /features/{name} enables a flag successfully.
 * - PUT /features/{name} disables a flag successfully.
 * - PUT /features/{name} returns 404 for an unknown flag name.
 * - PUT /features/{name} returns 400 when the request body is missing
 *   the "enabled" field.
 * - PUT /features/{name} returns 400 when the request body is empty.
 */
@WebMvcTest(FeatureController.class)
class FeatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FeatureFlagRepository repo;

    private FeatureFlag grafanaLink;
    private FeatureFlag llmLogs;

    /**
     * Sets up reusable FeatureFlag fixtures before each test.
     * Repository stubs are defined per-test to keep each case self-contained.
     */
    @BeforeEach
    void setUp() {
        // Reset the mock before each test to clear any save() calls made by
        // FeatureFlagBootstrap at application startup, which would otherwise
        // cause verify(repo).save(...) to count bootstrap invocations too.
        Mockito.reset(repo);

        grafanaLink = new FeatureFlag("grafanaLink", true, "Show the Grafana monitoring link in the admin panel");
        llmLogs = new FeatureFlag("llmLogs", false, "Expose LLM request logs in the admin panel");
    }


    // -------------------------------------------------------------------------
    // GET /features
    // -------------------------------------------------------------------------

    /**
     * Verifies that GET /features returns all flags as JSON
     * with HTTP 200.
     */
    @Test
    @DisplayName("GET /features - should return all feature flags")
    void shouldReturnAllFeatureFlags() throws Exception {

        when(repo.findAll()).thenReturn(List.of(grafanaLink, llmLogs));

        mockMvc.perform(get("/features"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("grafanaLink"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[1].name").value("llmLogs"))
                .andExpect(jsonPath("$[1].enabled").value(false));
    }

    /**
     * Verifies that GET /features returns an empty JSON array
     * when no flags are seeded.
     */
    @Test
    @DisplayName("GET /features - should return empty list when no flags exist")
    void shouldReturnEmptyListWhenNoFlagsExist() throws Exception {

        when(repo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/features"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }


    // -------------------------------------------------------------------------
    // PUT /features/{name}
    // -------------------------------------------------------------------------

    /**
     * Verifies that PUT /features/{name} with {"enabled": true}
     * enables an existing flag and returns the updated resource.
     */
    @Test
    @DisplayName("PUT /features/{name} - should enable an existing flag")
    void shouldEnableExistingFlag() throws Exception {

        FeatureFlag disabled = new FeatureFlag("llmLogs", false, "Expose LLM request logs in the admin panel");
        FeatureFlag enabled  = new FeatureFlag("llmLogs", true,  "Expose LLM request logs in the admin panel");

        when(repo.findById("llmLogs")).thenReturn(Optional.of(disabled));
        when(repo.save(any(FeatureFlag.class))).thenReturn(enabled);

        mockMvc.perform(put("/features/llmLogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("llmLogs"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(repo).save(any(FeatureFlag.class));
    }

    /**
     * Verifies that PUT /features/{name} with {"enabled": false}
     * disables an existing flag and returns the updated resource.
     */
    @Test
    @DisplayName("PUT /features/{name} - should disable an existing flag")
    void shouldDisableExistingFlag() throws Exception {

        FeatureFlag enabled  = new FeatureFlag("grafanaLink", true,  "Show the Grafana monitoring link in the admin panel");
        FeatureFlag disabled = new FeatureFlag("grafanaLink", false, "Show the Grafana monitoring link in the admin panel");

        when(repo.findById("grafanaLink")).thenReturn(Optional.of(enabled));
        when(repo.save(any(FeatureFlag.class))).thenReturn(disabled);

        mockMvc.perform(put("/features/grafanaLink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("grafanaLink"))
                .andExpect(jsonPath("$.enabled").value(false));

        verify(repo).save(any(FeatureFlag.class));
    }

    /**
     * Verifies that PUT /features/{name} returns HTTP 404
     * when the flag name does not exist in the repository.
     */
    @Test
    @DisplayName("PUT /features/{name} - should return 404 for unknown flag")
    void shouldReturn404ForUnknownFlag() throws Exception {

        when(repo.findById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(put("/features/non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isNotFound());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /features/{name} returns HTTP 400
     * when the request body is present but the "enabled" key is missing.
     */
    @Test
    @DisplayName("PUT /features/{name} - should return 400 when 'enabled' field is missing")
    void shouldReturn400WhenEnabledFieldIsMissing() throws Exception {

        when(repo.findById("grafanaLink")).thenReturn(Optional.of(grafanaLink));

        mockMvc.perform(put("/features/grafanaLink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("foo", "bar"))))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    /**
     * Verifies that PUT /features/{name} returns HTTP 400
     * when the request body is completely empty.
     */
    @Test
    @DisplayName("PUT /features/{name} - should return 400 when body is empty")
    void shouldReturn400WhenBodyIsEmpty() throws Exception {

        mockMvc.perform(put("/features/grafanaLink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }
}
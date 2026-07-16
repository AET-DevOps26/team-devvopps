package com.tum.user.controller;

import com.tum.user.model.AppSetting;
import com.tum.user.repository.AppSettingRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST controller for runtime app settings (prompt sections, token limit).
 *
 * GET is available to signed-in users and internal services; PUT is
 * restricted to ADMIN at the gateway.
 */
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.PUT, RequestMethod.OPTIONS})
public class SettingController {

    private static final Logger log = LoggerFactory.getLogger(SettingController.class);

    private final AppSettingRepository repo;

    /**
     * GET /settings
     *
     * Returns all settings.
     */
    @GetMapping
    public List<AppSetting> getAll() {
        return repo.findAll();
    }

    /**
     * PUT /settings/{name}
     *
     * Updates a setting's value. Body: {"value": "..."}.
     * Only existing (seeded) settings can be changed — no arbitrary creation.
     * monthlyTokenLimit must additionally be a positive integer.
     */
    @PutMapping("/{name}")
    public AppSetting update(@PathVariable(name = "name") String name, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must contain a non-empty \"value\"");
        }
        if ("monthlyTokenLimit".equals(name)) {
            try {
                if (Integer.parseInt(value.trim()) <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "monthlyTokenLimit must be a positive integer");
            }
        }
        AppSetting setting = repo.findById(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown setting: " + name));
        setting.setValue(value.trim());
        AppSetting saved = repo.save(setting);
        log.info("[Settings] '{}' updated ({} chars)", name, value.length());
        return saved;
    }
}

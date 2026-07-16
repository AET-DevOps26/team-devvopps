package com.tum.user.controller;

import com.tum.user.model.FeatureFlag;
import com.tum.user.repository.FeatureFlagRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST controller for runtime feature flags.
 *
 * GET is available to any signed-in user (clients need the flags to decide
 * what to render); PUT is restricted to ADMIN at the gateway.
 */
@RestController
@RequestMapping("/features")
@RequiredArgsConstructor
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.PUT, RequestMethod.OPTIONS})
public class FeatureController {

    private static final Logger log = LoggerFactory.getLogger(FeatureController.class);

    private final FeatureFlagRepository repo;

    /**
     * GET /features
     *
     * Returns all flags with their state and description.
     */
    @GetMapping
    public List<FeatureFlag> getAll() {
        return repo.findAll();
    }

    /**
     * PUT /features/{name}
     *
     * Enables or disables a flag. Body: {"enabled": true|false}.
     * Only existing (seeded) flags can be toggled — no arbitrary flag creation.
     */
    @PutMapping("/{name}")
    public FeatureFlag update(@PathVariable(name = "name") String name, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must contain \"enabled\": true|false");
        }
        FeatureFlag flag = repo.findById(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feature flag: " + name));
        flag.setEnabled(enabled);
        FeatureFlag saved = repo.save(flag);
        log.info("[Features] '{}' set to {}", name, enabled);
        return saved;
    }
}

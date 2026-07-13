package com.tum.user.security;

import com.tum.user.model.Role;
import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On startup, ensures an ADMIN account exists when ADMIN_EMAIL / ADMIN_PASSWORD
 * are configured. Idempotent: does nothing if the account already exists or the
 * env vars are blank. Passwords are hashed and never logged.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(UserRepository repo,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String adminEmail,
                          @Value("${app.admin.password:}") String adminPassword) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.info("[Auth] admin bootstrap skipped (ADMIN_EMAIL/ADMIN_PASSWORD not set)");
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (repo.findByEmail(email).isPresent()) {
            log.info("[Auth] admin bootstrap: account already exists for {}", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        repo.save(admin);
        log.info("[Auth] admin bootstrap: created ADMIN account for {}", email);
    }
}

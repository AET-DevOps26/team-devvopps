package com.tum.user.security;

import com.tum.user.model.Role;
import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminBootstrap.
 *
 * Covers the three branching paths:
 *   1. Skip when env vars are blank / null
 *   2. Skip when an admin account already exists
 *   3. Create a hashed ADMIN account when the email is new
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository repo;

    @Mock
    private PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------------------
    // Skip: blank credentials
    // ---------------------------------------------------------------------------

    @Test
    void skipsBootstrap_whenAdminEmailIsBlank() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "  ", "secret");

        bootstrap.run();

        verifyNoInteractions(repo, passwordEncoder);
    }

    @Test
    void skipsBootstrap_whenAdminPasswordIsBlank() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "admin@tum.de", "  ");

        bootstrap.run();

        verifyNoInteractions(repo, passwordEncoder);
    }

    @Test
    void skipsBootstrap_whenBothCredentialsAreEmpty() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "", "");

        bootstrap.run();

        verifyNoInteractions(repo, passwordEncoder);
    }

    // ---------------------------------------------------------------------------
    // Skip: account already exists
    // ---------------------------------------------------------------------------

    @Test
    void skipsBootstrap_whenAdminAccountAlreadyExists() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "admin@tum.de", "secret");
        when(repo.findByEmail("admin@tum.de")).thenReturn(Optional.of(new User()));

        bootstrap.run();

        verify(repo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    // ---------------------------------------------------------------------------
    // Create: new admin account
    // ---------------------------------------------------------------------------

    @Test
    void createsAdminAccount_whenEmailIsNew() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "admin@tum.de", "secret");
        when(repo.findByEmail("admin@tum.de")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        bootstrap.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@tum.de");
        assertThat(saved.getPassword()).isEqualTo("hashed-secret");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void normalisesEmailToLowercase_beforeLookupAndSave() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "  Admin@TUM.DE  ", "secret");
        when(repo.findByEmail("admin@tum.de")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        bootstrap.run();

        verify(repo).findByEmail("admin@tum.de");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@tum.de");
    }

    @Test
    void passwordIsHashed_notStoredInPlaintext() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "admin@tum.de", "plaintext");
        when(repo.findByEmail("admin@tum.de")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plaintext")).thenReturn("$2a$hash");

        bootstrap.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getPassword())
                .isEqualTo("$2a$hash")
                .doesNotContain("plaintext");
    }

    @Test
    void doesNotInteractWithPasswordEncoder_whenAccountAlreadyExists() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(repo, passwordEncoder, "admin@tum.de", "secret");
        when(repo.findByEmail("admin@tum.de")).thenReturn(Optional.of(new User()));

        bootstrap.run();

        verifyNoInteractions(passwordEncoder);
    }
}
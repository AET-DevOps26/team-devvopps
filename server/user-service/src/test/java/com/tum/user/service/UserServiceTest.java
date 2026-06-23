package com.tum.user.service;

import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 *
 * UserRepository is mocked to avoid database access.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repo;

    @InjectMocks
    private UserService service;

    /**
     * Verifies that createUser saves and returns the user.
     */
    @Test
    void createUser_savesUser() {
        User u = new User();
        when(repo.save(u)).thenReturn(u);

        User result = service.createUser(u);

        assertEquals(u, result);
    }

    /**
     * Verifies that getUser returns the correct user when it exists.
     */
    @Test
    void getUser_returnsUser() {
        User u = new User();
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        assertEquals(u, service.getUser(1L));
    }

    /**
     * Verifies that getUser throws a 404 when no user exists with the given ID.
     */
    @Test
    void getUser_throwsIfMissing() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getUser(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
    }

    /**
     * Verifies that getAllUsers returns the full list from the repository.
     */
    @Test
    void getAllUsers_returnsList() {
        when(repo.findAll()).thenReturn(List.of(new User()));

        assertEquals(1, service.getAllUsers().size());
    }
}
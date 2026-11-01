package com.pg.service;

import com.pg.dto.request.LoginRequest;
import com.pg.dto.request.RegisterRequest;
import com.pg.dto.response.AuthResponse;
import com.pg.entity.User;
import com.pg.entity.enums.Role;
import com.pg.exception.BusinessException;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.UserRepository;
import com.pg.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @InjectMocks AuthService authService;

    private RegisterRequest request(Role role) {
        RegisterRequest r = new RegisterRequest();
        r.setName("Asha");
        r.setEmail("  Asha@Example.com ");
        r.setPassword("secret123");
        r.setRole(role);
        return r;
    }

    @Test
    void nobodyCanSelfRegisterAsSuperAdmin() {
        assertThrows(UnauthorizedAccessException.class,
                () -> authService.register(request(Role.SUPER_ADMIN)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void tenantCanSelfRegisterAndGetsTheTenantRole() {
        when(passwordEncoder.encode("secret123")).thenReturn("HASH");
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

        AuthResponse response = authService.register(request(Role.TENANT));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.TENANT, saved.getValue().getRole());
        assertEquals("TENANT", response.getRole());
    }

    @Test
    void ownerRegistrationWorksAndNormalisesEmail() {
        when(passwordEncoder.encode("secret123")).thenReturn("HASH");
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

        AuthResponse response = authService.register(request(Role.OWNER));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.OWNER, saved.getValue().getRole());
        assertEquals("asha@example.com", saved.getValue().getEmail());
        assertEquals("HASH", saved.getValue().getPassword());
        assertEquals("token", response.getToken());
    }

    @Test
    void deactivatedAccountGetsTheSameGenericMessage() {
        User inactive = User.builder().email("gone@example.com").password("HASH").role(Role.TENANT).isActive(false).build();
        when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches("right", "HASH")).thenReturn(true); // even the RIGHT password
        LoginRequest req = new LoginRequest();
        req.setEmail("gone@example.com");
        req.setPassword("right");

        BusinessException e = assertThrows(BusinessException.class, () -> authService.login(req));

        assertEquals("Invalid email or password", e.getMessage());
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void unknownEmailStillRunsAPasswordCheckSoTimingDoesNotGiveItAway() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("DUMMY");
        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@example.com");
        req.setPassword("x");

        assertThrows(BusinessException.class, () -> authService.login(req));

        verify(passwordEncoder).matches("x", "DUMMY");
    }

    @Test
    void loginGivesSameMessageForUnknownEmailAndWrongPassword() {
        LoginRequest unknown = new LoginRequest();
        unknown.setEmail("nobody@example.com");
        unknown.setPassword("x");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        User user = User.builder().email("real@example.com").password("HASH").role(Role.OWNER).build();
        when(userRepository.findByEmail("real@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);
        LoginRequest wrong = new LoginRequest();
        wrong.setEmail("real@example.com");
        wrong.setPassword("wrong");

        BusinessException e1 = assertThrows(BusinessException.class, () -> authService.login(unknown));
        BusinessException e2 = assertThrows(BusinessException.class, () -> authService.login(wrong));
        assertEquals(e1.getMessage(), e2.getMessage()); // does not reveal which emails exist
    }
}

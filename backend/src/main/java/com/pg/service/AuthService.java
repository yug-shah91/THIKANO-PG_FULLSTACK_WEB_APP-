package com.pg.service;

import com.pg.exception.BusinessException;
import com.pg.dto.request.LoginRequest;
import com.pg.dto.request.RegisterRequest;
import com.pg.dto.response.AuthResponse;
import com.pg.entity.User;
import com.pg.entity.enums.Role;
import com.pg.exception.UnauthorizedAccessException;
import com.pg.repository.UserRepository;
import com.pg.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    static final String INVALID_LOGIN = "Invalid email or password";
    private volatile String dummyHash;

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordEncoder.encode("not-a-real-password");
            dummyHash = h;
        }
        return h;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new BusinessException("Email already registered: " + request.getEmail());
        }

        // SECURITY: public registration can create OWNER or TENANT accounts only.
        // SUPER_ADMIN can never be requested here - the first one is created at startup
        // by AdminSeeder. (A TENANT account has no PG until it books a room.)
        Role role = request.getRole() == null ? Role.OWNER : request.getRole();
        if (role != Role.OWNER && role != Role.TENANT) {
            throw new UnauthorizedAccessException("Only PG owners and tenants can self-register.");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(token).name(user.getName())
                .email(user.getEmail()).role(user.getRole().name())
                .message(role == Role.OWNER
                        ? "Registration successful. Set up your PG next."
                        : "Registration successful. Find a PG and book a room.")
                .build();
    }

    /** Who am I? Used by the frontend to check a stored session against the database. */
    @Transactional(readOnly = true)
    public AuthResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Account not found"));
        return AuthResponse.builder()
                .name(user.getName()).email(user.getEmail()).role(user.getRole().name())
                .message("OK")
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // ONE message for every failure (unknown email, wrong password, deactivated account), so a
        // stranger can not learn which emails exist or are deactivated. The password is also checked
        // for unknown emails (against a dummy hash) so the response time does not give it away either.
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase()).orElse(null);
        String hash = user != null ? user.getPassword() : dummyHash();
        boolean passwordOk = passwordEncoder.matches(request.getPassword(), hash);
        if (user == null || !passwordOk || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(INVALID_LOGIN);
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(token).name(user.getName())
                .email(user.getEmail()).role(user.getRole().name())
                .message("Login successful")
                .build();
    }
}

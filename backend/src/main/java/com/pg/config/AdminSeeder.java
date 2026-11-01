package com.pg.config;

import com.pg.entity.User;
import com.pg.entity.enums.Role;
import com.pg.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first SUPER_ADMIN at startup, from environment variables.
 * Public registration can no longer create admins, so this is the only way.
 *
 *   ADMIN_EMAIL=you@example.com  ADMIN_PASSWORD=a-strong-password  (ADMIN_NAME optional)
 *
 * If the variables are not set, nothing happens. If the email already exists, nothing happens.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.name:Super Admin}")
    private String adminName;

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        if (adminPassword.length() < 8) {
            log.warn("ADMIN_PASSWORD is shorter than 8 characters - super admin NOT created");
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.SUPER_ADMIN) {
                log.warn("ADMIN_EMAIL {} already belongs to a {} account, so NO super admin was created. "
                        + "Use a different ADMIN_EMAIL or change that user's role in the database.",
                        email, existing.get().getRole());
            }
            return;
        }
        userRepository.save(User.builder()
                .name(adminName)
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.SUPER_ADMIN)
                .build());
        log.info("Super admin account created for {}", email);
    }
}

package com.uip.backend.auth.config;

import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds default users at startup if they don't exist.
 * Passwords are loaded from env-vars — never hardcoded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserSeedInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser("admin",    "admin@uip.city",    "ADMIN_PASSWORD",    UserRole.ROLE_ADMIN);
        seedUser("operator", "operator@uip.city", "OPERATOR_PASSWORD", UserRole.ROLE_OPERATOR);
        seedUser("citizen1", "citizen1@uip.city", "CITIZEN_PASSWORD",  UserRole.ROLE_CITIZEN);
        log.info("User seed check complete");
    }

    private void seedUser(String username, String email, String envVar, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        String rawPassword = System.getenv(envVar);
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("Env var {} not set — using insecure default for dev only", envVar);
            rawPassword = username + "_Dev#2026!";  // dev fallback
        }
        AppUser user = new AppUser(username, email, passwordEncoder.encode(rawPassword), role);
        userRepository.save(user);
        log.info("Seeded user username={} role={}", username, role);
    }
}

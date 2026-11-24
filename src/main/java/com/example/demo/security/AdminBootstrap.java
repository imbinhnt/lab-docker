package com.example.demo.security;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Tạo tài khoản admin đầu tiên từ biến môi trường nếu chưa tồn tại.
 * Mật khẩu được băm bằng BCrypt; không hardcode hash trong migration SQL.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String rawPassword;

    public AdminBootstrap(UserRepository users,
                          RoleRepository roles,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.security.user:}") String username,
                          @Value("${app.security.password:}") String rawPassword) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.rawPassword = rawPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank()
                || rawPassword == null || rawPassword.isBlank()) {
            log.warn("Admin bootstrap skipped: app.security.user/password is not configured");
            return;
        }
        if (users.existsByUsername(username)) {
            log.debug("Admin user '{}' already exists, bootstrap skipped", username);
            return;
        }

        Role adminRole = roles.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + ADMIN_ROLE + " is missing; check Flyway migration V4"));

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setEnabled(true);
        admin.getRoles().add(adminRole);
        users.save(admin);

        log.info("Bootstrapped admin user '{}'", username);
    }
}

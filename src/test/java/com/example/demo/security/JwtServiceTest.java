package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

class JwtServiceTest {

    private static final String SECRET = "test-only-secret-change-me-please-32-bytes-minimum";
    private static final String OTHER_SECRET = "another-test-secret-change-me-32-bytes-min";

    private JwtService jwtService(Duration expiration) {
        return new JwtService(new JwtProperties(SECRET, expiration));
    }

    private User user(String username) {
        return new User(username, "irrelevant",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void generatesTokenWithSubjectAndExpiration() {
        JwtService jwtService = jwtService(Duration.ofMinutes(5));

        String token = jwtService.generateToken(user("alice"));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.isTokenValid(token, user("alice"))).isTrue();
        assertThat(jwtService.expirationSeconds()).isEqualTo(300);
    }

    @Test
    void tokenIsInvalidForDifferentUser() {
        JwtService jwtService = jwtService(Duration.ofMinutes(5));
        String token = jwtService.generateToken(user("alice"));

        assertThat(jwtService.isTokenValid(token, user("bob"))).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = jwtService(Duration.ofSeconds(-30));
        String token = jwtService.generateToken(user("alice"));

        assertThatThrownBy(() -> jwtService.isTokenValid(token, user("alice")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = jwtService(Duration.ofMinutes(5)).generateToken(user("alice"));
        JwtService other = new JwtService(new JwtProperties(OTHER_SECRET, Duration.ofMinutes(5)));

        assertThatThrownBy(() -> other.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }
}

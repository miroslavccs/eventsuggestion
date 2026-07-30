package com.codecraft.eventsuggestion.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "testSecretKeyForTestingPurposesOnly1234567890";

    private JwtUtil jwtUtil(long expirationMs) {
        return new JwtUtil(SECRET, expirationMs);
    }

    @Test
    void generateToken_extractEmail_roundTrips() {
        JwtUtil jwtUtil = jwtUtil(60_000);

        String token = jwtUtil.generateToken("jane@example.com");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("jane@example.com");
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        JwtUtil jwtUtil = jwtUtil(60_000);
        String token = jwtUtil.generateToken("jane@example.com");

        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_garbageString_returnsFalse() {
        JwtUtil jwtUtil = jwtUtil(60_000);

        assertThat(jwtUtil.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void validateToken_signedWithDifferentKey_returnsFalse() {
        JwtUtil signer = jwtUtil(60_000);
        JwtUtil verifier = new JwtUtil("aCompletelyDifferentSecretKeyValue1234567890", 60_000);
        String token = signer.generateToken("jane@example.com");

        assertThat(verifier.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtUtil jwtUtil = jwtUtil(-1_000);
        String expiredToken = jwtUtil.generateToken("jane@example.com");

        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    void extractEmail_invalidToken_throws() {
        JwtUtil jwtUtil = jwtUtil(60_000);

        assertThatThrownBy(() -> jwtUtil.extractEmail("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}

package com.acme.sportplatform.identity.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.acme.sportplatform.identity.domain.ExpiredTokenException;
import com.acme.sportplatform.identity.domain.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtSigningKeyConfigurationTest {
    private byte[] bytes(int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) 91);
        return result;
    }
    private String encoded(int length) {
        return Base64.getEncoder().encodeToString(bytes(length));
    }
    private JwtProperties properties(String secret) {
        JwtProperties result = mock(JwtProperties.class);
        when(result.secret()).thenReturn(secret);
        return result;
    }
    private String token(byte[] key, Instant expiration) {
        return Jwts.builder()
                .subject("test-admin@example.invalid")
                .issuer("sport-platform-test")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(expiration))
                .claim("uid", UUID.fromString("12345678-1234-4234-8234-123456789012").toString())
                .claim("roles", List.of("platform_admin"))
                .signWith(Keys.hmacShaKeyFor(key))
                .compact();
    }
    @Test void nullSecretFailsDuringServiceConstruction() {
        assertThrows(IllegalStateException.class, () -> new JwtService(properties(null)));
    }
    @Test void blankSecretFailsDuringServiceConstruction() {
        assertThrows(IllegalStateException.class, () -> new JwtService(properties("  ")));
    }
    @Test void invalidBase64FailsDuringServiceConstruction() {
        assertThrows(IllegalStateException.class, () -> new JwtService(properties("not-a-base64-key!")));
    }
    @Test void thirtyOneBytesAreInsufficient() {
        assertThrows(IllegalStateException.class, () -> new JwtService(properties(encoded(31))));
    }
    @Test void thirtyTwoBytesAreAccepted() {
        assertDoesNotThrow(() -> new JwtService(properties(encoded(32))));
    }
    @Test void fortyEightBytesAreAccepted() {
        assertDoesNotThrow(() -> new JwtService(properties(encoded(48))));
    }
    @Test void sixtyFourBytesAreAccepted() {
        assertDoesNotThrow(() -> new JwtService(properties(encoded(64))));
    }
    @Test void surroundingWhitespaceIsNotSilentlyIgnored() {
        assertThrows(IllegalStateException.class, () -> new JwtService(properties(" " + encoded(32))));
    }
    @Test void diagnosticsDoNotExposeTheSecretOrDecoderCause() {
        String supplied = "sensitive-config-value!";
        var error = assertThrows(IllegalStateException.class,
                () -> new JwtService(properties(supplied)));
        assertFalse(error.getMessage().contains(supplied));
        assertTrue(error.getMessage().contains("app.security.jwt.secret"));
        assertNull(error.getCause());
    }
    @Test void correctlySignedTokenStillParses() {
        var service = new JwtService(properties(encoded(32)));
        var claims = service.parse(token(bytes(32), Instant.now().plusSeconds(300)));
        assertEquals("test-admin@example.invalid", claims.getSubject());
        assertEquals(List.of("platform_admin"), claims.get("roles"));
    }
    @Test void signingKeyIsReadOnlyOnceAtConstruction() {
        var config = properties(encoded(32));
        var service = new JwtService(config);
        when(config.secret()).thenReturn("invalid!");
        assertEquals("test-admin@example.invalid",
                service.parse(token(bytes(32), Instant.now().plusSeconds(300))).getSubject());
        verify(config, times(1)).secret();
    }
    @Test void incorrectSignatureStillMapsToInvalidToken() {
        var service = new JwtService(properties(encoded(32)));
        byte[] other = bytes(32);
        other[0] = 92;
        assertThrows(InvalidTokenException.class,
                () -> service.parse(token(other, Instant.now().plusSeconds(300))));
    }
    @Test void expirationStillMapsToExpiredToken() {
        var service = new JwtService(properties(encoded(32)));
        assertThrows(ExpiredTokenException.class,
                () -> service.parse(token(bytes(32), Instant.now().minusSeconds(60))));
    }
}

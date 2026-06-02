package team.po.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import team.po.common.redis.RedisService;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

	private static final String SECRET = Base64.getEncoder()
		.encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

	@Mock
	private RedisService redisService;

	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		JwtProperties jwtProperties = new JwtProperties();
		jwtProperties.setSecret(SECRET);
		jwtProperties.setAccessTokenExpiration(Duration.ofMinutes(30));
		jwtProperties.setRefreshTokenExpiration(Duration.ofDays(14));
		jwtTokenProvider = new JwtTokenProvider(jwtProperties, redisService);
	}

	@Test
	void validateAccessToken_returnsFalseWhenSessionVersionWasRevoked() {
		when(redisService.getStringValue("ATSV:1")).thenReturn("1");
		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com", 0L);
		when(redisService.incrementValue("ATSV:1")).thenReturn(1L);

		jwtTokenProvider.revokeAccessTokens(1L);

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isFalse();
		verify(redisService, never()).expire("ATSV:1", Duration.ofMinutes(30));
	}

	@Test
	void validateAccessToken_returnsTrueWhenSessionVersionMatchesCurrentValue() {
		when(redisService.getStringValue("ATSV:1")).thenReturn("1");

		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com", 1L);

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isTrue();
	}

	@Test
	void validateAccessToken_returnsFalseWhenGeneratedWithRevokedSessionVersion() {
		when(redisService.getStringValue("ATSV:1")).thenReturn("1");
		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com", 0L);

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isFalse();
	}

	@Test
	void getSessionVersion_returnsRefreshTokenIssuedSessionVersion() {
		when(redisService.getStringValue("ATSV:1")).thenReturn("2");

		String refreshToken = jwtTokenProvider.generateRefreshToken(1L, "test@email.com");

		assertThat(jwtTokenProvider.getSessionVersion(refreshToken)).isEqualTo(2L);
	}

	@Test
	void generateAccessToken_initializesSessionVersionWhenMissing() {
		when(redisService.getStringValue("ATSV:1")).thenReturn(null, "0");

		jwtTokenProvider.generateAccessToken(1L, "test@email.com");

		verify(redisService).setIfAbsentValue("ATSV:1", 0L);
	}

	@Test
	void validateAccessToken_returnsFalseWhenSessionVersionKeyIsMissing() {
		when(redisService.getStringValue("ATSV:1")).thenReturn(null);
		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com", 0L);

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isFalse();
	}

	@Test
	void validateAccessToken_initializesSessionVersionForLegacyAccessToken() {
		when(redisService.getStringValue("ATSV:1")).thenReturn(null, "0");
		String accessToken = generateLegacyAccessToken(1L, "test@email.com");

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isTrue();
		verify(redisService).setIfAbsentValue("ATSV:1", 0L);
	}

	@Test
	void validateAccessToken_returnsFalseWhenSessionVersionStorageFails() {
		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com", 0L);
		when(redisService.getStringValue("ATSV:1"))
			.thenThrow(new DataAccessResourceFailureException("redis unavailable"));

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isFalse();
	}

	private String generateLegacyAccessToken(Long userId, String email) {
		SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
		Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));

		return Jwts.builder()
			.subject(email)
			.claim("userId", userId)
			.claim("tokenType", "access")
			.issuedAt(new Date())
			.expiration(Date.from(expiresAt))
			.signWith(key)
			.compact();
	}
}

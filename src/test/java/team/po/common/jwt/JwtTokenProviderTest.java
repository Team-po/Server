package team.po.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

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

		verify(redisService).setIfAbsentValue("ATSV:1", "0");
	}

	@Test
	void validateAccessToken_returnsFalseWhenSessionVersionKeyIsMissing() {
		when(redisService.getStringValue("ATSV:1")).thenReturn(null);
		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com", 0L);

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isFalse();
	}

	@Test
	void validateAccessToken_returnsFalseWhenSessionVersionStorageFails() {
		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com", 0L);
		when(redisService.getStringValue("ATSV:1"))
			.thenThrow(new DataAccessResourceFailureException("redis unavailable"));

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isFalse();
	}
}

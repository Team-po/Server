package team.po.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
		when(redisService.getStringValue("ATSV:1")).thenReturn(null, "1");
		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com");
		when(redisService.incrementValue("ATSV:1")).thenReturn(1L);

		jwtTokenProvider.revokeAccessTokens(1L);

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isFalse();
		verify(redisService, never()).expire("ATSV:1", Duration.ofMinutes(30));
	}

	@Test
	void validateAccessToken_returnsTrueWhenSessionVersionMatchesCurrentValue() {
		when(redisService.getStringValue("ATSV:1")).thenReturn("1", "1");

		String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@email.com");

		assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isTrue();
	}
}

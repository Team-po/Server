package team.po.common.jwt;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import team.po.common.redis.RedisDao;

@Slf4j
@Component
public class JwtTokenProvider {

	public static final String BEARER_TYPE = "Bearer";
	private static final String USER_ID_KEY = "userId";
	private static final String TOKEN_TYPE_KEY = "tokenType";
	private static final String ACCESS_TOKEN_TYPE = "access";
	private static final String REFRESH_TOKEN_TYPE = "refresh";
	private static final String REFRESH_TOKEN_PREFIX = "RT:";

	private final JwtProperties jwtProperties;
	private final RedisDao redisDao;
	private final SecretKey key;

	public JwtTokenProvider(JwtProperties jwtProperties, RedisDao redisDao) {
		this.jwtProperties = jwtProperties;
		this.redisDao = redisDao;
		this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
	}

	public JwtToken generateToken(Long userId, String email) {
		Date now = new Date();

		Date accessTokenExpiresAt = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration().toMillis());
		String accessToken = Jwts.builder()
			.subject(email)
			.claim(USER_ID_KEY, userId)
			.claim(TOKEN_TYPE_KEY, ACCESS_TOKEN_TYPE)
			.issuedAt(now)
			.expiration(accessTokenExpiresAt)
			.signWith(key)
			.compact();

		Date refreshTokenExpiresAt = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration().toMillis());
		String refreshToken = Jwts.builder()
			.subject(email)
			.claim(USER_ID_KEY, userId)
			.claim(TOKEN_TYPE_KEY, REFRESH_TOKEN_TYPE)
			.issuedAt(now)
			.expiration(refreshTokenExpiresAt)
			.signWith(key)
			.compact();

		redisDao.setValue(createRefreshTokenKey(email), refreshToken, jwtProperties.getRefreshTokenExpiration());

		return new JwtToken(BEARER_TYPE, accessToken, refreshToken, Instant.ofEpochMilli(accessTokenExpiresAt.getTime()));
	}

	public Authentication getAuthentication(String accessToken) {
		Claims claims = parseClaims(accessToken);
		Number userIdClaim = claims.get(USER_ID_KEY, Number.class);

		if (userIdClaim == null) {
			throw new IllegalArgumentException("Token does not contain user id.");
		}

		UserPrincipal principal = new UserPrincipal(userIdClaim.longValue(), claims.getSubject());

		return new UsernamePasswordAuthenticationToken(principal, accessToken, principal.getAuthorities());
	}

	public Claims parseClaims(String token) {
		try {
			return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
		} catch (ExpiredJwtException exception) {
			return exception.getClaims();
		}
	}

	public boolean validateAccessToken(String accessToken) {
		return validateToken(accessToken, ACCESS_TOKEN_TYPE);
	}

	public boolean validateRefreshToken(String refreshToken) {
		return validateToken(refreshToken, REFRESH_TOKEN_TYPE);
	}

	public String getEmail(String token) {
		return parseClaims(token).getSubject();
	}

	public Long getUserId(String token) {
		Number userIdClaim = parseClaims(token).get(USER_ID_KEY, Number.class);
		if (userIdClaim == null) {
			throw new IllegalArgumentException("Token does not contain user id.");
		}
		return userIdClaim.longValue();
	}

	public void deleteRefreshToken(String email) {
		redisDao.deleteValue(createRefreshTokenKey(email));
	}

	private boolean validateToken(String token, String tokenType) {
		try {
			Claims claims = Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();

			if (!tokenType.equals(claims.get(TOKEN_TYPE_KEY, String.class))) {
				log.debug("{} token type mismatch", tokenType);
				return false;
			}

			if (claims.get(USER_ID_KEY, Number.class) == null || claims.getSubject() == null) {
				log.debug("{} token missing principal claims", tokenType);
				return false;
			}

			return true;
		} catch (ExpiredJwtException exception) {
			log.debug("{} token expired", tokenType, exception);
		} catch (JwtException | IllegalArgumentException exception) {
			log.debug("{} token invalid", tokenType, exception);
		}
		return false;
	}

	private String createRefreshTokenKey(String email) {
		return REFRESH_TOKEN_PREFIX + email;
	}
}

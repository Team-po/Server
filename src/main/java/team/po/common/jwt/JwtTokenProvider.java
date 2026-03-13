package team.po.common.jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
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
	public static final String AUTHORITIES_KEY = "auth";
	private static final String REFRESH_TOKEN_PREFIX = "RT:";
	private static final String AUTHORITIES_DELIMITER = ",";

	private final JwtProperties jwtProperties;
	private final RedisDao redisDao;
	private final SecretKey key;

	public JwtTokenProvider(JwtProperties jwtProperties, RedisDao redisDao) {
		this.jwtProperties = jwtProperties;
		this.redisDao = redisDao;
		this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
	}

	public JwtToken generateToken(String email, Collection<? extends GrantedAuthority> authorities) {
		Date now = new Date();
		String subject = email;
		String authorityClaim = authorities.stream()
			.map(GrantedAuthority::getAuthority)
			.reduce((left, right) -> left + AUTHORITIES_DELIMITER + right)
			.orElse("");

		Date accessTokenExpiresAt = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration().toMillis());
		String accessToken = Jwts.builder()
			.subject(subject)
			.claim(AUTHORITIES_KEY, authorityClaim)
			.issuedAt(now)
			.expiration(accessTokenExpiresAt)
			.signWith(key)
			.compact();

		Date refreshTokenExpiresAt = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration().toMillis());
		String refreshToken = Jwts.builder()
			.subject(subject)
			.issuedAt(now)
			.expiration(refreshTokenExpiresAt)
			.signWith(key)
			.compact();

		redisDao.setValue(createRefreshTokenKey(email), refreshToken, jwtProperties.getRefreshTokenExpiration());

		return new JwtToken(BEARER_TYPE, accessToken, refreshToken);
	}

	public Authentication getAuthentication(String accessToken) {
		Claims claims = parseClaims(accessToken);
		Object authoritiesClaim = claims.get(AUTHORITIES_KEY);

		if (authoritiesClaim == null) {
			throw new IllegalArgumentException("Token does not contain authorities.");
		}

		Collection<? extends GrantedAuthority> authorities = Arrays.stream(authoritiesClaim.toString().split(AUTHORITIES_DELIMITER))
			.filter(authority -> !authority.isBlank())
			.map(SimpleGrantedAuthority::new)
			.toList();

		UserDetails principal = new User(claims.getSubject(), "", authorities);

		return new UsernamePasswordAuthenticationToken(principal, accessToken, authorities);
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
		return validateToken(accessToken, "access");
	}

	public boolean validateRefreshToken(String refreshToken) {
		return validateToken(refreshToken, "refresh");
	}

	public String getEmail(String token) {
		return parseClaims(token).getSubject();
	}

	public void deleteRefreshToken(String email) {
		redisDao.deleteValue(createRefreshTokenKey(email));
	}

	private boolean validateToken(String token, String tokenType) {
		try {
			Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token);
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

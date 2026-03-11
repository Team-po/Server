package team.po.common.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

	private String secret;
	private Duration accessTokenExpiration;
	private Duration refreshTokenExpiration;

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public Duration getAccessTokenExpiration() {
		return accessTokenExpiration;
	}

	public void setAccessTokenExpiration(Duration accessTokenExpiration) {
		this.accessTokenExpiration = accessTokenExpiration;
	}

	public Duration getRefreshTokenExpiration() {
		return refreshTokenExpiration;
	}

	public void setRefreshTokenExpiration(Duration refreshTokenExpiration) {
		this.refreshTokenExpiration = refreshTokenExpiration;
	}
}

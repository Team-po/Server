package team.po.common.jwt;

import java.time.Instant;

public record JwtToken(
	String grantType,
	String accessToken,
	String refreshToken,
	Instant accessTokenExpiresAt
) {
}

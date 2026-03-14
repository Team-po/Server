package team.po.common.jwt;

public record JwtToken(
	String grantType,
	String accessToken,
	String refreshToken
) {
}

package team.po.feature.user.dto;

import java.time.Instant;

public record SignInResponse(String accessToken, String refreshToken, Instant expiresAt) {
}

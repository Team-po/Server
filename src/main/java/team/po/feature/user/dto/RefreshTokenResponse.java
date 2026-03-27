package team.po.feature.user.dto;

import java.time.Instant;

public record RefreshTokenResponse(String accessToken, Instant expiresAt) {
}

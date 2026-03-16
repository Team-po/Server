package team.po.feature.user.dto;

import java.time.LocalDateTime;

public record SignInResponse(String accessToken, String refreshToken, LocalDateTime expiresAt) {
}

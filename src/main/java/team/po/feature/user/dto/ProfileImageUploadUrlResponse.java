package team.po.feature.user.dto;

import java.time.Instant;

public record ProfileImageUploadUrlResponse(
	String uploadUrl,
	String objectKey,
	String contentType,
	Instant expiresAt
) {
}

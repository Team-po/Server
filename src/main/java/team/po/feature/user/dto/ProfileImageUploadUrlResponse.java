package team.po.feature.user.dto;

import java.time.Instant;
import java.util.Map;

public record ProfileImageUploadUrlResponse(
	String uploadUrl,
	Map<String, String> formFields,
	String objectKey,
	String contentType,
	Long maxFileSizeBytes,
	Instant expiresAt
) {
}

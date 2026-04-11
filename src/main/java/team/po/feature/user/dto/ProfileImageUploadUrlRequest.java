package team.po.feature.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileImageUploadUrlRequest(
	@NotBlank(message = "이미지 Content-Type은 필수입니다.")
	String contentType
) {
}

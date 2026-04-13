package team.po.feature.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EditProfileRequest(
	String description,
	@NotBlank(message = "닉네임 입력은 필수입니다.")
	String nickname,
	@NotNull(message = "레벨 선택은 필수입니다.")
	@Min(value = 1, message = "레벨은 1 이상이어야 합니다.")
	@Max(value = 5, message = "레벨은 5 이하여야 합니다.")
	Integer level,
	@Size(max = 255, message = "프로필 이미지 키는 255자 이하여야 합니다.")
	// 1. images/sign-up/{파일명}.{확장자}
	// 2. images/users/{숫자}/{파일명}.{확장자}
	// 만 허용
	@Pattern(
		regexp = "^images/(sign-up|users/\\d+)/[A-Za-z0-9-]+\\.(jpg|png|gif|webp)$",
		message = "프로필 이미지 키 형식이 올바르지 않습니다."
	)
	String profileImageKey
) {
}

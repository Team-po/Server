package team.po.feature.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EditProfileRequest(
	String description,
	@NotBlank(message = "닉네임 입력은 필수입니다.")
	String nickname,
	@NotNull(message = "레벨 선택은 필수입니다.")
	@Min(value = 1, message = "레벨은 1 이상이어야 합니다.")
	@Max(value = 5, message = "레벨은 5 이하여야 합니다.")
	Integer level
	) {
}

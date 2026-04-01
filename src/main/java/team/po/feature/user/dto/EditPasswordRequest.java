package team.po.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditPasswordRequest(
	@NotBlank(message = "현재 비밀번호 입력은 필수입니다.")
	String currentPassword,
	@NotBlank(message = "바꿀 비밀번호 입력은 필수입니다.")
	@Size(min = 8,message = "비밀번호는 8글자 이상이어야 합니다.")
	String afterPassword
) {
}

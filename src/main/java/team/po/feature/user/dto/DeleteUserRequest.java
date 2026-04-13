package team.po.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteUserRequest(
	@NotBlank(message = "회원 탈퇴시 비밀번호 입력은 필수입니다.")
	@Size(min = 8, message = "비밀번호는 8글자 이상이어야 합니다.")
	String password
) {
}

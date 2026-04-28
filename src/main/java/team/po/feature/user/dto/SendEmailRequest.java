package team.po.feature.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendEmailRequest(
	@Email(message = "이메일 형식이 아닙니다.")
	@NotBlank(message = "이메일 입력은 필수입니다.")
	String email
) {
}

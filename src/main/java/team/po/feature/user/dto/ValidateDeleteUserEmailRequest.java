package team.po.feature.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ValidateDeleteUserEmailRequest(
	@NotNull(message = "인증번호 입력은 필수입니다.")
	@Min(value = 100000, message = "인증번호는 6자리 숫자입니다.")
	@Max(value = 999999, message = "인증번호는 6자리 숫자입니다.")
	Integer authNumber
) {
}

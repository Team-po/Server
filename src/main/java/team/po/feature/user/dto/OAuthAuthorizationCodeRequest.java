package team.po.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OAuthAuthorizationCodeRequest(
	@NotBlank(message = "인가 코드가 필요합니다.")
	String code,
	@Min(value = 1, message = "레벨은 1 이상이어야 합니다.")
	@Max(value = 5, message = "레벨은 5 이하여야 합니다.")
	Integer level
) {
}

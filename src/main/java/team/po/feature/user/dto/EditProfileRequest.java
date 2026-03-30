package team.po.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EditProfileRequest(
	String description,
	@NotBlank
	String nickName,
	@NotNull
	Integer level
	) {
}

package team.po.feature.match.dto;

import jakarta.validation.constraints.NotNull;
import team.po.feature.match.enums.Role;

public record ProjectRequestDto(
	@NotNull(message = "역할 선택은 필수입니다.")
	Role role,
	String projectTitle,
	String projectDescription,
	String projectMvp
) {
}
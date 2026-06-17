package team.po.feature.projectgroup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectGroupNameRequest(
	@NotBlank(message = "팀 이름은 필수입니다.")
	@Size(max = 255, message = "팀 이름은 255자 이하여야 합니다.")
	String projectName
) {
}

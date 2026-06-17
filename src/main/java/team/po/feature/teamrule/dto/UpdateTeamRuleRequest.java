package team.po.feature.teamrule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTeamRuleRequest(
	@NotBlank(message = "팀 룰 내용은 필수입니다.")
	@Size(max = 10000, message = "팀 룰 내용은 10000자 이하여야 합니다.")
	String content,
	@NotNull(message = "팀 룰 버전은 필수입니다.")
	Long version
) {
}

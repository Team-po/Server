package team.po.feature.projectgroup.dto;

import jakarta.validation.constraints.NotNull;
import team.po.feature.projectgroup.domain.MemberRole;

public record CreateProjectGroupMemberRequest(
	@NotNull(message = "사용자 식별자는 필수입니다.")
	Long userId,
	@NotNull(message = "팀 역할은 필수입니다.")
	MemberRole role
) {
}

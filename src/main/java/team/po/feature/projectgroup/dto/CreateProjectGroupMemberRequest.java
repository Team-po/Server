package team.po.feature.projectgroup.dto;

import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
import jakarta.validation.constraints.NotNull;

public record CreateProjectGroupMemberRequest(
	@NotNull(message = "사용자 식별자는 필수입니다.")
	Long userId,
	@NotNull(message = "팀 역할은 필수입니다.")
	MemberRole role,
	@NotNull(message = "그룹 역할은 필수입니다.")
	GroupRole groupRole,
	Boolean admin
) {
}

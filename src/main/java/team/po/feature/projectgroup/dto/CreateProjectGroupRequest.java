package team.po.feature.projectgroup.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;

public record CreateProjectGroupRequest(
	@NotNull(message = "방장 사용자 식별자는 필수입니다.")
	Long hostUserId,
	@NotNull(message = "매칭 식별자는 필수입니다.")
	@Positive(message = "매칭 식별자는 양수여야 합니다.")
	Long matchId,
	@NotEmpty(message = "팀 구성원 목록은 필수입니다.")
	@Size(min = 4, max = 4, message = "팀 인원은 정확히 4명이어야 합니다.")
	List<@Valid @NotNull(message = "구성원 정보는 null일 수 없습니다.") CreateProjectGroupMemberRequest> members,
	@NotBlank(message = "프로젝트 그룹 이름은 필수입니다.")
	String projectName,
	@NotBlank(message = "프로젝트 제목은 필수입니다.")
	String projectTitle,
	String projectDescription,
	String projectMvp,
	ProjectGroupStatus status
) {
}

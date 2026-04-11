package team.po.feature.projectgroup.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectGroupRequest(
	@NotNull(message = "매칭 식별자는 필수입니다.")
	Long groupId,
	@NotNull(message = "팀 구성원 목록은 필수입니다.")
	@Size(min = 4, max = 4, message = "팀 인원은 정확히 4명이어야 합니다.")
	List<CreateProjectGroupMemberRequest> members,
	@NotBlank(message = "프로젝트 그룹 이름은 필수입니다.")
	String projectName,
	@NotBlank(message = "프로젝트 제목은 필수입니다.")
	String projectTitle,
	String projectDescription,
	String projectMvp
) {
}

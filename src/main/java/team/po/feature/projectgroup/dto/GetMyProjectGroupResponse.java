package team.po.feature.projectgroup.dto;

import java.util.List;

public record GetMyProjectGroupResponse(
	Long projectGroupId,
	String projectName,
	String projectTitle,
	String projectDescription,
	String projectMvp,
	String status,
	List<MemberInfo> members
) {
	public record MemberInfo(
		Long userId,
		String nickname,
		String memberRole,
		String groupRole,
		boolean admin
	) {
	}
}

package team.po.feature.projectgroup.dto;

import java.util.List;

public record GetMyProjectGroupResponse(
	Long currentUserId,
	Long projectGroupId,
	String projectName,
	String projectTitle,
	String projectDescription,
	String projectMvp,
	List<MemberInfo> members
) {
	public record MemberInfo(
		Long userId,
		String nickname,
		String profileImage,
		Integer level,
		Integer temperature,
		String memberRole,
		String groupRole,
		boolean admin
	) {
	}
}

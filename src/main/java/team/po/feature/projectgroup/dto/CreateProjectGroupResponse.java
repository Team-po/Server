package team.po.feature.projectgroup.dto;

public record CreateProjectGroupResponse(
	Long groupId,
	String projectName,
	String projectTitle,
	String status,
	Integer memberCount
) {
}

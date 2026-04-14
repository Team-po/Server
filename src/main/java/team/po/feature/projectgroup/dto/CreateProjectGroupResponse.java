package team.po.feature.projectgroup.dto;

public record CreateProjectGroupResponse(
	Long projectGroupId,
	String projectName,
	String projectTitle,
	String status,
	Integer memberCount
) {
}

package team.po.feature.match.dto;

public record MatchProjectResponse(
	Long matchId,
	String projectTitle,
	String projectDescription,
	String projectMvp
) {
}

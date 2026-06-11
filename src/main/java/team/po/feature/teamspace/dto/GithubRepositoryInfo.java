package team.po.feature.teamspace.dto;

public record GithubRepositoryInfo(
	Long githubRepositoryId,
	String owner,
	String repoName,
	String fullName,
	String defaultBranch,
	boolean privateRepository
) {
}

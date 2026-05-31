package team.po.feature.teamspace.dto;

public record GithubPullRequestSyncContext(
	Long installationId,
	String owner,
	String repoName
) {
}

package team.po.feature.teamspace.dto;

import java.time.Instant;

public record GithubPullRequestInfo(
	Long githubPullRequestId,
	Long pullNumber,
	String title,
	Long authorGithubUserId,
	String authorGithubUsername,
	String state,
	Instant mergedAt,
	Integer additions,
	Integer deletions,
	Integer changedFiles,
	Integer linkedIssueCount,
	String htmlUrl
) {
}

package team.po.feature.teamspace.dto;

import java.time.Instant;

public record GithubPullRequestSummary(
	Long githubPullRequestId,
	Long pullNumber,
	String title,
	Long authorGithubUserId,
	String authorGithubUsername,
	String state,
	Instant mergedAt,
	String htmlUrl
) {
}

package team.po.feature.teamspace.dto;

import java.time.Instant;
import java.util.List;

public record GithubWeeklySummaryData(
	Instant periodStart,
	Instant periodEnd,
	List<RepositoryActivity> repositories
) {
	public int pullRequestCount() {
		return repositories.stream()
			.mapToInt(repository -> repository.pullRequests().size())
			.sum();
	}

	public int issueCount() {
		return repositories.stream()
			.mapToInt(repository -> repository.issues().size())
			.sum();
	}

	public record RepositoryActivity(
		Long githubRepositoryId,
		String owner,
		String repoName,
		String fullName,
		List<PullRequest> pullRequests,
		List<Issue> issues
	) {
	}

	public record PullRequest(
		Long githubPullRequestId,
		Long pullNumber,
		String title,
		String body,
		Long authorGithubUserId,
		String authorGithubUsername,
		String state,
		Instant createdAt,
		Instant updatedAt,
		Instant closedAt,
		Instant mergedAt,
		String htmlUrl
	) {
	}

	public record Issue(
		Long githubIssueId,
		Long issueNumber,
		String title,
		String body,
		Long authorGithubUserId,
		String authorGithubUsername,
		String state,
		Instant createdAt,
		Instant updatedAt,
		Instant closedAt,
		String htmlUrl
	) {
	}
}

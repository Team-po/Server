package team.po.feature.teamspace.dto;

import java.util.List;

public record GetGithubRepositoryContributionResponse(
	Long githubRepositoryId,
	String repoName,
	String fullName,
	List<ContributorResponse> contributors
) {
	public record ContributorResponse(
		Long githubUserId,
		String githubUsername,
		long mergedPrCount,
		long linkedIssueCount,
		long additions,
		long deletions,
		long changedFiles,
		long contributionScore
	) {
	}
}

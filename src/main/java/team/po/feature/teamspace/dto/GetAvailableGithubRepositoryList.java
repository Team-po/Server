package team.po.feature.teamspace.dto;

import java.util.List;

public record GetAvailableGithubRepositoryList(
	List<RepositoryResponse> repositories
) {
	public record RepositoryResponse(
		Long githubRepositoryId,
		String repoName,
		String fullName
	) {
	}
}

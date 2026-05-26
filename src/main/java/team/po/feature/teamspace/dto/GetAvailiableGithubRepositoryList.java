package team.po.feature.teamspace.dto;

import java.util.List;

public record GetAvailiableGithubRepositoryList(
	List<RepositoryResponse> repositories
) {
	public record RepositoryResponse(
		Long githubRepositoryId,
		String repoName,
		String fullName
	) {
	}
}

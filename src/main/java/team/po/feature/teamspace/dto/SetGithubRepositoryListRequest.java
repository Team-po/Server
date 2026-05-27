package team.po.feature.teamspace.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record SetGithubRepositoryListRequest(
	@NotNull(message = "Github Repository 목록은 필수입니다.")
	List<@NotNull Long> githubRepositoryIds
) {
}

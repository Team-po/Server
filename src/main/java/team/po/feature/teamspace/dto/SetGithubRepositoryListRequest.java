package team.po.feature.teamspace.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SetGithubRepositoryListRequest(
	@NotEmpty(message = "등록할 Github Repository를 1개 이상 선택해야 합니다.")
	List<@NotNull Long> githubRepositoryIds
) {
}

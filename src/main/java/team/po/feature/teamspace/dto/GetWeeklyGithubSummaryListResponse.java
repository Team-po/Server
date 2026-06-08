package team.po.feature.teamspace.dto;

import java.util.List;

public record GetWeeklyGithubSummaryListResponse(
	List<GetWeeklyGithubSummaryResponse> summaries
) {
}

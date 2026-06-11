package team.po.feature.teamspace.dto;

import java.time.Instant;

public record GetWeeklyGithubSummaryResponse(
	Long weeklyGithubSummaryId,
	Instant periodStart,
	Instant periodEnd,
	int sourcePrCount,
	int sourceIssueCount,
	GithubWeeklySummaryContent summary
) {
}

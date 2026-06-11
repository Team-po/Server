package team.po.feature.teamspace.dto;

import java.time.Instant;

public record GenerateWeeklyGithubSummaryResponse(
	Long weeklyGithubSummaryId,
	Instant periodStart,
	Instant periodEnd,
	int sourcePrCount,
	int sourceIssueCount,
	GithubWeeklySummaryContent summary
) {
}

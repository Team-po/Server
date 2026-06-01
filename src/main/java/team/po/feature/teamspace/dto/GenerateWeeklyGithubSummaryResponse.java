package team.po.feature.teamspace.dto;

import java.time.Instant;
import java.util.Map;

public record GenerateWeeklyGithubSummaryResponse(
	Long weeklyGithubSummaryId,
	Instant periodStart,
	Instant periodEnd,
	int sourcePrCount,
	int sourceIssueCount,
	Map<String, Object> summary
) {
}

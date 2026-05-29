package team.po.feature.match.event;

import java.util.List;

public record MatchRejectedEvent(
	Long matchSessionId,
	Long rejectedUserId,
	List<Long> remainingMemberUserIds
) {
}

package team.po.feature.match.event;

import java.util.List;

public record MatchOrphanSessionCleanedEvent(
	Long matchSessionId,
	List<Long> restoreMemberUserIds
) {
}

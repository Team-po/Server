package team.po.feature.match.event;

import java.util.List;

public record MatchCompletedEvent(
	Long matchSessionId,
	Long projectGroupId,
	List<Long> memberUserIds
) {
}

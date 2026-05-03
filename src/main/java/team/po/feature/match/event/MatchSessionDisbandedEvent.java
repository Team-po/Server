package team.po.feature.match.event;

import java.util.List;

public record MatchSessionDisbandedEvent(
	Long matchSessionId,
	Long hostUserId,
	List<Long> restoreMemberUserIds
) {
}

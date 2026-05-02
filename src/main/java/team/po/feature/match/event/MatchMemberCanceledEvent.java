package team.po.feature.match.event;

import java.util.List;

public record MatchMemberCanceledEvent(
	Long matchSessionId,
	Long canceledUserId,
	List<Long> remainingMemberUserIds
) {
}

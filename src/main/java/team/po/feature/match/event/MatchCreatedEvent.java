package team.po.feature.match.event;

import java.util.List;

/**
 * 매칭 세션이 생성되었을 때 발행되는 이벤트
 * @param matchingSessionId 생성된 세션 ID
 * @param memberUserIds 알림을 받을 유저 ID 리스트 (Host + Members)
 */
public record MatchCreatedEvent(
	Long matchingSessionId,
	List<Long> memberUserIds
) {
}

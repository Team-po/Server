package team.po.feature.match.event;

public record MatchAcceptedEvent(
	Long matchSessionId,
	Long acceptedUserId
) {
}

package team.po.feature.chat.dto;

import java.time.Instant;

import team.po.feature.chat.domain.ChatReadState;

public record ChatReadStateResponse(
	Long lastReadMessageId,
	Instant updatedAt
) {
	public static ChatReadStateResponse from(ChatReadState readState) {
		Long lastReadMessageId = readState.getLastReadMessage() == null
			? null
			: readState.getLastReadMessage().getId();
		return new ChatReadStateResponse(lastReadMessageId, readState.getUpdatedAt());
	}
}

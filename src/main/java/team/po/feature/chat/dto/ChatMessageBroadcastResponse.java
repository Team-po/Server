package team.po.feature.chat.dto;

import java.time.Instant;

import team.po.feature.chat.domain.ChatMessageType;

public record ChatMessageBroadcastResponse(
	Long messageId,
	Long projectGroupId,
	Long senderUserId,
	String senderNickname,
	String senderProfileImage,
	ChatMessageType type,
	String content,
	Instant createdAt
) {
	public static ChatMessageBroadcastResponse from(ChatMessageResponse response) {
		return new ChatMessageBroadcastResponse(
			response.messageId(),
			response.projectGroupId(),
			response.senderUserId(),
			response.senderNickname(),
			response.senderProfileImage(),
			response.type(),
			response.content(),
			response.createdAt()
		);
	}
}

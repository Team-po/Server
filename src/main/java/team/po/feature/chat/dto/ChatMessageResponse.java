package team.po.feature.chat.dto;

import java.time.Instant;

import team.po.feature.chat.domain.ChatMessage;
import team.po.feature.chat.domain.ChatMessageType;

public record ChatMessageResponse(
	Long messageId,
	Long projectGroupId,
	Long senderUserId,
	String senderNickname,
	String senderProfileImage,
	ChatMessageType type,
	String content,
	Instant createdAt,
	boolean mine
) {
	public static ChatMessageResponse from(ChatMessage message, Long requesterUserId) {
		Long senderUserId = message.getSender().getId();
		return new ChatMessageResponse(
			message.getId(),
			message.getChatRoom().getProjectGroup().getId(),
			senderUserId,
			message.getSender().getNickname(),
			message.getSender().getProfileImage(),
			message.getType(),
			message.getContent(),
			message.getCreatedAt(),
			senderUserId.equals(requesterUserId)
		);
	}
}

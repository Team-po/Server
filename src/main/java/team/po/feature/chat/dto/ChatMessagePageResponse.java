package team.po.feature.chat.dto;

import java.util.List;

public record ChatMessagePageResponse(
	List<ChatMessageResponse> messages,
	Long nextBeforeMessageId,
	boolean hasNext
) {
}

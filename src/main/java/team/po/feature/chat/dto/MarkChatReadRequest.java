package team.po.feature.chat.dto;

import jakarta.validation.constraints.NotNull;

public record MarkChatReadRequest(
	@NotNull(message = "읽은 메시지 식별자는 필수입니다.")
	Long lastReadMessageId
) {
}

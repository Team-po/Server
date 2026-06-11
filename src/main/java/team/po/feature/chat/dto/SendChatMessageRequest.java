package team.po.feature.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
	@NotBlank(message = "채팅 메시지를 입력해 주세요.")
	@Size(max = 2000, message = "채팅 메시지는 2000자 이하로 입력해 주세요.")
	String content
) {
}

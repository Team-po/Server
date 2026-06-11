package team.po.feature.chat.controller;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.jwt.UserPrincipal;
import team.po.feature.chat.dto.ChatMessageResponse;
import team.po.feature.chat.dto.SendChatMessageRequest;
import team.po.feature.chat.service.ChatMessageService;

@Controller
@RequiredArgsConstructor
public class ChatMessageSocketController {

	private final ChatMessageService chatMessageService;
	private final SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/project-groups/{projectGroupId}/chat/messages")
	public void sendMessage(
		@DestinationVariable Long projectGroupId,
		@Valid @Payload SendChatMessageRequest request,
		Principal principal
	) {
		Long requesterUserId = getRequesterUserId(principal);
		ChatMessageResponse response = chatMessageService.sendMessage(projectGroupId, requesterUserId, request);
		messagingTemplate.convertAndSend("/topic/project-groups/" + projectGroupId + "/chat/messages", response);
	}

	private Long getRequesterUserId(Principal principal) {
		if (principal instanceof Authentication authentication
			&& authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
			return userPrincipal.id();
		}

		throw new AccessDeniedException("채팅 메시지를 보내려면 인증이 필요합니다.");
	}
}

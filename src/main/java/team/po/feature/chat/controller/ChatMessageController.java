package team.po.feature.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.feature.chat.dto.ChatMessagePageResponse;
import team.po.feature.chat.dto.ChatReadStateResponse;
import team.po.feature.chat.dto.MarkChatReadRequest;
import team.po.feature.chat.service.ChatMessageService;
import team.po.feature.user.domain.Users;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/project-groups/{projectGroupId}/chat")
public class ChatMessageController {

	private final ChatMessageService chatMessageService;

	@Operation(summary = "팀 스페이스 채팅 메시지 목록 조회 API")
	@GetMapping("/messages")
	public ResponseEntity<ChatMessagePageResponse> getMessages(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@RequestParam(required = false) Long beforeMessageId,
		@RequestParam(defaultValue = "30") Integer size
	) {
		return ResponseEntity.ok(
			chatMessageService.getMessages(projectGroupId, requester.getId(), beforeMessageId, size)
		);
	}

	@Operation(summary = "팀 스페이스 채팅 읽음 상태 갱신 API")
	@PatchMapping("/read")
	public ResponseEntity<ChatReadStateResponse> markRead(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@Valid @RequestBody MarkChatReadRequest request
	) {
		return ResponseEntity.ok(chatMessageService.markRead(projectGroupId, requester.getId(), request));
	}
}

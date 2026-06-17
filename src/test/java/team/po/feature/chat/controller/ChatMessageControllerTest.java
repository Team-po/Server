package team.po.feature.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.CustomExceptionHandler;
import team.po.feature.chat.domain.ChatMessageType;
import team.po.feature.chat.dto.ChatMessagePageResponse;
import team.po.feature.chat.dto.ChatMessageResponse;
import team.po.feature.chat.dto.ChatReadStateResponse;
import team.po.feature.chat.dto.MarkChatReadRequest;
import team.po.feature.chat.service.ChatMessageService;
import team.po.feature.user.domain.Users;

@WebMvcTest(ChatMessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class ChatMessageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatMessageService chatMessageService;

	@MockitoBean
	private LoginUserArgumentResolver loginUserArgumentResolver;

	private Users mockUser;

	@BeforeEach
	void setUp() throws Exception {
		mockUser = Users.builder()
			.email("tester@example.com")
			.nickname("tester")
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(mockUser, "id", 1L);

		when(loginUserArgumentResolver.supportsParameter(any())).thenReturn(true);
		when(loginUserArgumentResolver.resolveArgument(any(), any(), any(), any())).thenReturn(mockUser);

		UserPrincipal principal = new UserPrincipal(1L, "tester@example.com");
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getMessages_returnsChatMessagePage() throws Exception {
		ChatMessageResponse message = new ChatMessageResponse(
			1000L,
			10L,
			1L,
			"tester",
			null,
			ChatMessageType.TEXT,
			"안녕하세요",
			Instant.parse("2026-06-11T12:00:00Z"),
			true
		);
		when(chatMessageService.getMessages(10L, 1L, null, 30))
			.thenReturn(new ChatMessagePageResponse(List.of(message), null, false));

		mockMvc.perform(get("/api/project-groups/{projectGroupId}/chat/messages", 10L)
				.param("size", "30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages[0].messageId").value(1000L))
			.andExpect(jsonPath("$.messages[0].senderNickname").value("tester"))
			.andExpect(jsonPath("$.messages[0].content").value("안녕하세요"))
			.andExpect(jsonPath("$.messages[0].mine").value(true))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void markRead_returnsUpdatedReadState() throws Exception {
		when(chatMessageService.markRead(10L, 1L, new MarkChatReadRequest(1000L)))
			.thenReturn(new ChatReadStateResponse(1000L, Instant.parse("2026-06-11T12:01:00Z")));

		mockMvc.perform(patch("/api/project-groups/{projectGroupId}/chat/read", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "lastReadMessageId": 1000
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lastReadMessageId").value(1000L));

		verify(chatMessageService).markRead(10L, 1L, new MarkChatReadRequest(1000L));
	}
}

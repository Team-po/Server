package team.po.feature.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;

@ExtendWith(MockitoExtension.class)
class ChatChannelInterceptorTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	private ChatChannelInterceptor chatChannelInterceptor;

	@BeforeEach
	void setUp() {
		chatChannelInterceptor = new ChatChannelInterceptor(jwtTokenProvider, projectGroupMemberRepository);
	}

	@Test
	void preSend_setsAuthenticationUser_whenConnectFrameHasValidBearerToken() {
		Authentication authentication = new UsernamePasswordAuthenticationToken(
			new UserPrincipal(1L, "tester@example.com"),
			"access-token",
			List.of()
		);
		when(jwtTokenProvider.validateAccessToken("access-token")).thenReturn(true);
		when(jwtTokenProvider.getAuthentication("access-token")).thenReturn(authentication);

		Message<?> result = chatChannelInterceptor.preSend(
			createMessage(StompCommand.CONNECT, "Bearer access-token"),
			mock(MessageChannel.class)
		);

		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
		assertThat(accessor).isNotNull();
		assertThat(accessor.getUser()).isEqualTo(authentication);
	}

	@Test
	void preSend_throwsAccessDenied_whenConnectFrameHasInvalidBearerToken() {
		when(jwtTokenProvider.validateAccessToken("expired-token")).thenReturn(false);

		assertThatThrownBy(() -> chatChannelInterceptor.preSend(
			createMessage(StompCommand.CONNECT, "Bearer expired-token"),
			mock(MessageChannel.class)
		)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void preSend_allowsSubscribe_whenAuthenticatedUserIsProjectGroupMember() {
		Authentication authentication = authentication(1L);
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(true);

		Message<?> result = chatChannelInterceptor.preSend(
			createSubscriptionMessage("/topic/project-groups/10/chat/messages", authentication),
			mock(MessageChannel.class)
		);

		assertThat(result).isNotNull();
	}

	@Test
	void preSend_throwsAccessDenied_whenAuthenticatedUserSubscribesOtherProjectGroupChat() {
		Authentication authentication = authentication(1L);
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(99L, 1L)).thenReturn(false);

		assertThatThrownBy(() -> chatChannelInterceptor.preSend(
			createSubscriptionMessage("/topic/project-groups/99/chat/messages", authentication),
			mock(MessageChannel.class)
		)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void preSend_allowsSend_whenDestinationUsesApplicationPrefix() {
		Message<?> result = chatChannelInterceptor.preSend(
			createSendMessage("/app/project-groups/10/chat/messages"),
			mock(MessageChannel.class)
		);

		assertThat(result).isNotNull();
	}

	@Test
	void preSend_throwsAccessDenied_whenClientSendsDirectlyToBrokerTopic() {
		assertThatThrownBy(() -> chatChannelInterceptor.preSend(
			createSendMessage("/topic/project-groups/10/chat/messages"),
			mock(MessageChannel.class)
		)).isInstanceOf(AccessDeniedException.class);
	}

	private Message<byte[]> createMessage(StompCommand command, String authorizationHeader) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setNativeHeader("Authorization", authorizationHeader);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private Message<byte[]> createSubscriptionMessage(String destination, Authentication authentication) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination(destination);
		accessor.setUser(authentication);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private Message<byte[]> createSendMessage(String destination) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination(destination);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private Authentication authentication(Long userId) {
		return new UsernamePasswordAuthenticationToken(
			new UserPrincipal(userId, "tester@example.com"),
			"access-token",
			List.of()
		);
	}
}

package team.po.feature.chat.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;

@Component
@RequiredArgsConstructor
public class ChatChannelInterceptor implements ChannelInterceptor {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String APPLICATION_DESTINATION_PREFIX = "/app/";
	private static final Pattern CHAT_TOPIC_DESTINATION_PATTERN =
		Pattern.compile("^/topic/project-groups/(\\d+)/chat/messages$");

	private final JwtTokenProvider jwtTokenProvider;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}

		if (accessor.getCommand() == StompCommand.SEND) {
			validateSendDestination(accessor);
			return message;
		}

		if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
			validateChatSubscription(accessor);
			return message;
		}

		if (accessor.getCommand() != StompCommand.CONNECT) {
			return message;
		}

		String accessToken = resolveAccessToken(accessor);
		if (!StringUtils.hasText(accessToken) || !jwtTokenProvider.validateAccessToken(accessToken)) {
			throw new AccessDeniedException("유효하지 않거나 만료된 채팅 연결 토큰입니다.");
		}

		Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);
		accessor.setUser(authentication);
		return message;
	}

	private void validateSendDestination(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		if (!StringUtils.hasText(destination) || !destination.startsWith(APPLICATION_DESTINATION_PREFIX)) {
			throw new AccessDeniedException("채팅 메시지는 애플리케이션 목적지로만 전송할 수 있습니다.");
		}
	}

	private void validateChatSubscription(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		if (!StringUtils.hasText(destination)) {
			return;
		}

		Matcher matcher = CHAT_TOPIC_DESTINATION_PATTERN.matcher(destination);
		if (!matcher.matches()) {
			return;
		}

		Long projectGroupId = Long.parseLong(matcher.group(1));
		Long requesterUserId = getRequesterUserId(accessor);
		boolean isMember = projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(
			projectGroupId,
			requesterUserId
		);

		if (!isMember) {
			throw new AccessDeniedException("팀 스페이스 멤버만 채팅을 구독할 수 있습니다.");
		}
	}

	private Long getRequesterUserId(StompHeaderAccessor accessor) {
		if (accessor.getUser() instanceof Authentication authentication
			&& authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
			return userPrincipal.id();
		}

		throw new AccessDeniedException("채팅을 구독하려면 인증이 필요합니다.");
	}

	private String resolveAccessToken(StompHeaderAccessor accessor) {
		List<String> authorizationHeaders = accessor.getNativeHeader(AUTHORIZATION_HEADER);
		if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
			return null;
		}

		String authorizationHeader = authorizationHeaders.get(0);
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
			return null;
		}

		return authorizationHeader.substring(BEARER_PREFIX.length());
	}
}

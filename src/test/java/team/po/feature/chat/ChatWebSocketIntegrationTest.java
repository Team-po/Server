package team.po.feature.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import jakarta.persistence.EntityManager;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.feature.chat.domain.ChatMessage;
import team.po.feature.chat.dto.SendChatMessageRequest;
import team.po.feature.chat.repository.ChatMessageRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class ChatWebSocketIntegrationTest {

	private static final String ACCESS_TOKEN = "integration-test-token";

	@LocalServerPort
	private int port;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private ChatMessageRepository chatMessageRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private SimpUserRegistry simpUserRegistry;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			insertUser(1L, "sender@example.com", "sender");
			insertUser(2L, "teammate@example.com", "teammate");
			insertProjectGroup(10L);
			insertProjectGroupMember(1L, 10L);
			insertProjectGroupMember(2L, 10L);
		});

		Authentication authentication = new UsernamePasswordAuthenticationToken(
			new UserPrincipal(1L, "sender@example.com"),
			ACCESS_TOKEN,
			List.of()
		);
		when(jwtTokenProvider.validateAccessToken(ACCESS_TOKEN)).thenReturn(true);
		when(jwtTokenProvider.getAuthentication(ACCESS_TOKEN)).thenReturn(authentication);
	}

	@Test
	void websocketSendMessage_persistsAndBroadcastsMessageToProjectGroupTopic() throws Exception {
		BlockingQueue<Map<String, Object>> receivedMessages = new LinkedBlockingQueue<>();
		BlockingQueue<Throwable> connectionErrors = new LinkedBlockingQueue<>();
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());

		StompSession session = null;
		try {
			session = connect(stompClient, connectionErrors);
			String chatTopicDestination = "/topic/project-groups/10/chat/messages";
			session.subscribe(chatTopicDestination, new StompFrameHandler() {
				@Override
				public Type getPayloadType(StompHeaders headers) {
					return Map.class;
				}

				@SuppressWarnings("unchecked")
				@Override
				public void handleFrame(StompHeaders headers, Object payload) {
					receivedMessages.add((Map<String, Object>)payload);
				}
			});
			awaitSubscription(chatTopicDestination);
			assertThat(connectionErrors).isEmpty();

			session.send(
				"/app/project-groups/10/chat/messages",
				new SendChatMessageRequest("통합 테스트 채팅 메시지")
			);

			Map<String, Object> response = receivedMessages.poll(5, TimeUnit.SECONDS);

			assertThat(connectionErrors).isEmpty();
			assertThat(response).isNotNull();
			assertThat(response.get("projectGroupId")).isEqualTo(10);
			assertThat(response.get("senderUserId")).isEqualTo(1);
			assertThat(response.get("senderNickname")).isEqualTo("sender");
			assertThat(response.get("content")).isEqualTo("통합 테스트 채팅 메시지");
			assertThat(response).doesNotContainKey("mine");

			List<ChatMessage> savedMessages = chatMessageRepository.findAll();
			assertThat(savedMessages).hasSize(1);
			assertThat(savedMessages.get(0).getContent()).isEqualTo("통합 테스트 채팅 메시지");
		} finally {
			if (session != null && session.isConnected()) {
				session.disconnect();
			}
			stompClient.stop();
		}
	}

	private void awaitSubscription(String destination) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			boolean subscribed = simpUserRegistry.findSubscriptions(
				subscription -> destination.equals(subscription.getDestination())
			).stream().anyMatch(subscription ->
				"sender@example.com".equals(subscription.getSession().getUser().getName())
			);
			if (subscribed) {
				return;
			}
			TimeUnit.MILLISECONDS.sleep(50);
		}
		throw new AssertionError("채팅 토픽 구독이 등록되지 않았습니다: " + destination);
	}

	private StompSession connect(WebSocketStompClient stompClient, BlockingQueue<Throwable> connectionErrors) throws Exception {
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add("Authorization", "Bearer " + ACCESS_TOKEN);

		return stompClient.connectAsync(
				"ws://localhost:" + port + "/ws",
					new WebSocketHttpHeaders(),
					connectHeaders,
					new StompSessionHandlerAdapter() {
						@Override
						public Type getPayloadType(StompHeaders headers) {
							return String.class;
						}

						@Override
						public void handleFrame(StompHeaders headers, Object payload) {
							connectionErrors.add(new IllegalStateException(
								"STOMP ERROR frame headers=" + headers + ", payload=" + payload
							));
						}

						@Override
						public void handleException(
							StompSession session,
							StompCommand command,
							StompHeaders headers,
							byte[] payload,
							Throwable exception
						) {
							connectionErrors.add(exception);
						}

						@Override
						public void handleTransportError(StompSession session, Throwable exception) {
							connectionErrors.add(exception);
						}

						@Override
						public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
						}
					}
				)
				.get(5, TimeUnit.SECONDS);
	}

	private void insertUser(Long id, String email, String nickname) {
		entityManager.createNativeQuery("""
			INSERT INTO users (
				id, email, nickname, temperature, level, is_github_login, created_at
			) VALUES (
				:id, :email, :nickname, 50, 1, false, :createdAt
			)
			""")
			.setParameter("id", id)
			.setParameter("email", email)
			.setParameter("nickname", nickname)
			.setParameter("createdAt", Instant.parse("2026-06-11T00:00:00Z"))
			.executeUpdate();
	}

	private void insertProjectGroup(Long id) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group (
				id, project_name, project_title, status, created_at
			) VALUES (
				:id, 'TeamPo', 'TeamPo', 'ACTIVE', :createdAt
			)
			""")
			.setParameter("id", id)
			.setParameter("createdAt", Instant.parse("2026-06-11T00:00:00Z"))
			.executeUpdate();
	}

	private void insertProjectGroupMember(Long userId, Long projectGroupId) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group_member (
				user_id, project_group_id, role, group_role, is_admin, is_finish_agreed, created_at
			) VALUES (
				:userId, :projectGroupId, 'BACKEND', 'MEMBER', false, false, :createdAt
			)
			""")
			.setParameter("userId", userId)
			.setParameter("projectGroupId", projectGroupId)
			.setParameter("createdAt", Instant.parse("2026-06-11T00:00:00Z"))
			.executeUpdate();
	}
}

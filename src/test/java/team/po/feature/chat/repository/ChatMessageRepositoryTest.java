package team.po.feature.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import team.po.feature.chat.domain.ChatMessage;

@DataJpaTest
@ActiveProfiles("h2")
class ChatMessageRepositoryTest {

	@Autowired
	private ChatMessageRepository chatMessageRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findLatestMessages_returnsNonDeletedMessagesBeforeCursorInDescendingOrder() {
		insertUser(1L, "sender@example.com", "sender");
		insertProjectGroup(10L);
		insertChatRoom(100L, 10L);
		insertChatMessage(1000L, 100L, 1L, "첫 메시지", null);
		insertChatMessage(1001L, 100L, 1L, "두 번째 메시지", null);
		insertChatMessage(1002L, 100L, 1L, "세 번째 메시지", null);
		insertChatMessage(1003L, 100L, 1L, "커서 이후 메시지", null);
		insertChatMessage(1004L, 100L, 1L, "삭제된 메시지", Instant.parse("2026-06-11T12:10:00Z"));
		entityManager.flush();
		entityManager.clear();

		List<ChatMessage> messages = chatMessageRepository.findLatestMessages(100L, 1003L, PageRequest.of(0, 3));

		assertThat(messages).extracting(ChatMessage::getId)
			.containsExactly(1002L, 1001L, 1000L);
		assertThat(messages).extracting(ChatMessage::getContent)
			.containsExactly("세 번째 메시지", "두 번째 메시지", "첫 메시지");
	}

	private void insertUser(Long id, String email, String nickname) {
		entityManager.createNativeQuery("""
			INSERT INTO users (
				id, email, nickname, temperature, level, is_github_login, created_at
			) VALUES (
				:id, :email, :nickname, 50, 1, false, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("email", email)
			.setParameter("nickname", nickname)
			.executeUpdate();
	}

	private void insertProjectGroup(Long id) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group (
				id, project_name, project_title, status, created_at
			) VALUES (
				:id, 'TeamPo', 'TeamPo', 'ACTIVE', CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.executeUpdate();
	}

	private void insertChatRoom(Long id, Long projectGroupId) {
		entityManager.createNativeQuery("""
			INSERT INTO chat_room (
				id, project_group_id, created_at
			) VALUES (
				:id, :projectGroupId, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("projectGroupId", projectGroupId)
			.executeUpdate();
	}

	private void insertChatMessage(Long id, Long chatRoomId, Long senderUserId, String content, Instant deletedAt) {
		entityManager.createNativeQuery("""
			INSERT INTO chat_message (
				id,
				chat_room_id,
				sender_user_id,
				type,
				content,
				created_at,
				deleted_at
			) VALUES (
				:id,
				:chatRoomId,
				:senderUserId,
				'TEXT',
				:content,
				CURRENT_TIMESTAMP,
				:deletedAt
			)
			""")
			.setParameter("id", id)
			.setParameter("chatRoomId", chatRoomId)
			.setParameter("senderUserId", senderUserId)
			.setParameter("content", content)
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}
}

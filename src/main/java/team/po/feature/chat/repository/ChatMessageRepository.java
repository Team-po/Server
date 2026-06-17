package team.po.feature.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	@Query("""
		select message
		from ChatMessage message
		join fetch message.sender
		where message.chatRoom.id = :chatRoomId
			and message.deletedAt is null
			and (:beforeMessageId is null or message.id < :beforeMessageId)
		order by message.id desc
		""")
	List<ChatMessage> findLatestMessages(
		@Param("chatRoomId") Long chatRoomId,
		@Param("beforeMessageId") Long beforeMessageId,
		Pageable pageable
	);

	Optional<ChatMessage> findByIdAndChatRoom_Id(Long messageId, Long chatRoomId);
}

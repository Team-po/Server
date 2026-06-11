package team.po.feature.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.chat.domain.ChatReadState;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {
	Optional<ChatReadState> findByChatRoom_IdAndUser_Id(Long chatRoomId, Long userId);
}

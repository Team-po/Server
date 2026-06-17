package team.po.feature.chat.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.user.domain.Users;

@Entity
@Table(
	name = "chat_read_state",
	uniqueConstraints = @UniqueConstraint(name = "uq_chat_read_state_room_user", columnNames = {"chat_room_id", "user_id"})
)
@NoArgsConstructor
@Getter
public class ChatReadState {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private Users user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "last_read_message_id")
	private ChatMessage lastReadMessage;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Builder
	public ChatReadState(ChatRoom chatRoom, Users user) {
		this.chatRoom = chatRoom;
		this.user = user;
		this.updatedAt = Instant.now();
	}

	public void markRead(ChatMessage message) {
		if (lastReadMessage != null
			&& lastReadMessage.getId() != null
			&& message.getId() != null
			&& lastReadMessage.getId() >= message.getId()) {
			return;
		}

		this.lastReadMessage = message;
		this.updatedAt = Instant.now();
	}
}

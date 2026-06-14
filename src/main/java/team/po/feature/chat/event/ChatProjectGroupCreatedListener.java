package team.po.feature.chat.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import team.po.feature.chat.service.ChatRoomService;
import team.po.feature.projectgroup.event.ProjectGroupCreatedEvent;

@Component
@RequiredArgsConstructor
public class ChatProjectGroupCreatedListener {

	private final ChatRoomService chatRoomService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(ProjectGroupCreatedEvent event) {
		chatRoomService.createRoomIfAbsent(event.projectGroupId());
	}
}

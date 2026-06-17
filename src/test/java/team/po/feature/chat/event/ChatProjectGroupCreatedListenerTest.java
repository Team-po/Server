package team.po.feature.chat.event;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.po.feature.chat.service.ChatRoomService;
import team.po.feature.projectgroup.event.ProjectGroupCreatedEvent;

@ExtendWith(MockitoExtension.class)
class ChatProjectGroupCreatedListenerTest {

	@Mock
	private ChatRoomService chatRoomService;

	private ChatProjectGroupCreatedListener listener;

	@BeforeEach
	void setUp() {
		listener = new ChatProjectGroupCreatedListener(chatRoomService);
	}

	@Test
	void handle_createsChatRoomForCreatedProjectGroup() {
		listener.handle(new ProjectGroupCreatedEvent(10L));

		verify(chatRoomService).createRoomIfAbsent(10L);
	}
}

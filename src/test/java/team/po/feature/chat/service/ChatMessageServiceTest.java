package team.po.feature.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.chat.domain.ChatMessage;
import team.po.feature.chat.domain.ChatMessageType;
import team.po.feature.chat.domain.ChatReadState;
import team.po.feature.chat.domain.ChatRoom;
import team.po.feature.chat.dto.ChatMessagePageResponse;
import team.po.feature.chat.dto.ChatMessageResponse;
import team.po.feature.chat.dto.ChatReadStateResponse;
import team.po.feature.chat.dto.MarkChatReadRequest;
import team.po.feature.chat.dto.SendChatMessageRequest;
import team.po.feature.chat.repository.ChatMessageRepository;
import team.po.feature.chat.repository.ChatReadStateRepository;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

	@Mock
	private ChatRoomService chatRoomService;

	@Mock
	private ChatMessageRepository chatMessageRepository;

	@Mock
	private ChatReadStateRepository chatReadStateRepository;

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	private ChatMessageService chatMessageService;

	@BeforeEach
	void setUp() {
		chatMessageService = new ChatMessageService(
			chatRoomService,
			chatMessageRepository,
			chatReadStateRepository,
			projectGroupMemberRepository
		);
	}

	@Test
	void sendMessage_savesTextMessage_whenRequesterIsActiveProjectGroupMember() {
		Users requester = mockUser(1L, "나");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember membership = mockMember(projectGroup, requester);
		ChatRoom chatRoom = mockChatRoom(100L, projectGroup);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(membership));
		when(chatRoomService.getOrCreateRoom(10L)).thenReturn(chatRoom);
		when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
			ChatMessage saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 1000L);
			ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-06-11T12:00:00Z"));
			return saved;
		});

		ChatMessageResponse response = chatMessageService.sendMessage(
			10L,
			1L,
			new SendChatMessageRequest("  안녕하세요 팀원들  ")
		);

		assertThat(response.messageId()).isEqualTo(1000L);
		assertThat(response.projectGroupId()).isEqualTo(10L);
		assertThat(response.senderUserId()).isEqualTo(1L);
		assertThat(response.senderNickname()).isEqualTo("나");
		assertThat(response.content()).isEqualTo("안녕하세요 팀원들");
		assertThat(response.type()).isEqualTo(ChatMessageType.TEXT);
		assertThat(response.mine()).isTrue();
		assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-06-11T12:00:00Z"));
	}

	@Test
	void sendMessage_throwsForbidden_whenRequesterIsNotProjectGroupMember() {
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 99L))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> chatMessageService.sendMessage(
			10L,
			99L,
			new SendChatMessageRequest("권한 없는 메시지")
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_GROUP_ACCESS_DENIED);

		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void sendMessage_throwsBadRequest_whenContentIsBlank() {
		Users requester = mockUser(1L, "나");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(mockMember(projectGroup, requester)));

		assertThatThrownBy(() -> chatMessageService.sendMessage(
			10L,
			1L,
			new SendChatMessageRequest("   ")
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.CHAT_MESSAGE_CONTENT_REQUIRED);

		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void sendMessage_throwsBadRequest_whenContentExceedsLimit() {
		Users requester = mockUser(1L, "나");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(mockMember(projectGroup, requester)));

		assertThatThrownBy(() -> chatMessageService.sendMessage(
			10L,
			1L,
			new SendChatMessageRequest("a".repeat(2001))
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.CHAT_MESSAGE_CONTENT_TOO_LONG);

		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void sendMessage_throwsForbidden_whenProjectGroupIsFinished() {
		Users requester = mockUser(1L, "나");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.FINISHED);
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(mockMember(projectGroup, requester)));

		assertThatThrownBy(() -> chatMessageService.sendMessage(
			10L,
			1L,
			new SendChatMessageRequest("종료된 팀 메시지")
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.CHAT_MESSAGE_WRITE_NOT_ALLOWED);

		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void getMessages_returnsNewestMessagesInAscendingOrderWithCursor() {
		Users requester = mockUser(1L, "나");
		Users teammate = mockUser(2L, "팀원");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ChatRoom chatRoom = mockChatRoom(100L, projectGroup);
		ChatMessage older = mockMessage(1001L, chatRoom, teammate, "먼저 온 메시지");
		ChatMessage newer = mockMessage(1002L, chatRoom, requester, "최근 메시지");
		ChatMessage extra = mockMessage(1000L, chatRoom, teammate, "다음 페이지 메시지");

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(mockMember(projectGroup, requester)));
		when(chatRoomService.getOrCreateRoom(10L)).thenReturn(chatRoom);
		when(chatMessageRepository.findLatestMessages(eq(100L), eq(null), any(Pageable.class)))
			.thenReturn(List.of(newer, older, extra));

		ChatMessagePageResponse response = chatMessageService.getMessages(10L, 1L, null, 2);

		assertThat(response.messages()).extracting(ChatMessageResponse::messageId)
			.containsExactly(1001L, 1002L);
		assertThat(response.messages().get(0).mine()).isFalse();
		assertThat(response.messages().get(1).mine()).isTrue();
		assertThat(response.nextBeforeMessageId()).isEqualTo(1001L);
		assertThat(response.hasNext()).isTrue();
	}

	@Test
	void markRead_upsertsLastReadMessage_whenMessageBelongsToProjectGroupRoom() {
		Users requester = mockUser(1L, "나");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember membership = mockMember(projectGroup, requester);
		ChatRoom chatRoom = mockChatRoom(100L, projectGroup);
		ChatMessage message = mockMessage(1000L, chatRoom, requester, "읽은 메시지");
		ChatReadState state = new ChatReadState(chatRoom, requester);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(membership));
		when(chatRoomService.getOrCreateRoom(10L)).thenReturn(chatRoom);
		when(chatMessageRepository.findByIdAndChatRoom_Id(1000L, 100L)).thenReturn(Optional.of(message));
		when(chatReadStateRepository.findByChatRoom_IdAndUser_Id(100L, 1L)).thenReturn(Optional.of(state));

		ChatReadStateResponse response = chatMessageService.markRead(
			10L,
			1L,
			new MarkChatReadRequest(1000L)
		);

		assertThat(state.getLastReadMessage()).isEqualTo(message);
		assertThat(response.lastReadMessageId()).isEqualTo(1000L);
		verify(chatReadStateRepository, never()).save(any());
	}

	private Users mockUser(Long userId, String nickname) {
		Users user = Users.builder()
			.email("user" + userId + "@example.com")
			.password("encoded")
			.nickname(nickname)
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}

	private ProjectGroup mockProjectGroup(Long projectGroupId, ProjectGroupStatus status) {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(status)
			.build();
		ReflectionTestUtils.setField(projectGroup, "id", projectGroupId);
		return projectGroup;
	}

	private ProjectGroupMember mockMember(ProjectGroup projectGroup, Users user) {
		return new ProjectGroupMember(projectGroup, user, MemberRole.BACKEND, GroupRole.MEMBER);
	}

	private ChatRoom mockChatRoom(Long chatRoomId, ProjectGroup projectGroup) {
		ChatRoom chatRoom = new ChatRoom(projectGroup);
		ReflectionTestUtils.setField(chatRoom, "id", chatRoomId);
		ReflectionTestUtils.setField(chatRoom, "createdAt", Instant.parse("2026-06-11T10:00:00Z"));
		return chatRoom;
	}

	private ChatMessage mockMessage(Long messageId, ChatRoom chatRoom, Users sender, String content) {
		ChatMessage message = new ChatMessage(chatRoom, sender, ChatMessageType.TEXT, content);
		ReflectionTestUtils.setField(message, "id", messageId);
		ReflectionTestUtils.setField(message, "createdAt", Instant.parse("2026-06-11T12:00:00Z"));
		return message;
	}
}

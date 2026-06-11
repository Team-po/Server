package team.po.feature.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.feature.chat.domain.ChatRoom;
import team.po.feature.chat.repository.ChatRoomRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

	@Mock
	private ChatRoomRepository chatRoomRepository;

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	private ChatRoomService chatRoomService;

	@BeforeEach
	void setUp() {
		chatRoomService = new ChatRoomService(chatRoomRepository, projectGroupRepository);
	}

	@Test
	void getOrCreateRoom_returnsExistingRoomWithoutLock_whenRoomAlreadyExists() {
		ProjectGroup projectGroup = mockProjectGroup(10L);
		ChatRoom existingRoom = mockChatRoom(100L, projectGroup);
		when(chatRoomRepository.findByProjectGroup_Id(10L)).thenReturn(Optional.of(existingRoom));

		ChatRoom result = chatRoomService.getOrCreateRoom(10L);

		assertThat(result).isEqualTo(existingRoom);
		verify(projectGroupRepository, never()).findByIdForUpdate(anyLong());
		verify(chatRoomRepository, never()).save(any());
	}

	@Test
	void getOrCreateRoom_reusesRoomCreatedByConcurrentRequest_afterProjectGroupLock() {
		ProjectGroup projectGroup = mockProjectGroup(10L);
		ChatRoom concurrentRoom = mockChatRoom(100L, projectGroup);
		when(chatRoomRepository.findByProjectGroup_Id(10L))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(concurrentRoom));
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(projectGroup));

		ChatRoom result = chatRoomService.getOrCreateRoom(10L);

		assertThat(result).isEqualTo(concurrentRoom);
		verify(chatRoomRepository, never()).save(any());
	}

	@Test
	void getOrCreateRoomForUpdate_locksProjectGroupAndCreatesRoom_whenStillAbsent() {
		ProjectGroup projectGroup = mockProjectGroup(10L);
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(projectGroup));
		when(chatRoomRepository.findByProjectGroup_Id(10L)).thenReturn(Optional.empty());
		when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0, ChatRoom.class));

		ChatRoom result = chatRoomService.getOrCreateRoomForUpdate(10L);

		assertThat(result.getProjectGroup()).isEqualTo(projectGroup);
		verify(projectGroupRepository).findByIdForUpdate(10L);
		verify(chatRoomRepository).save(any(ChatRoom.class));
	}

	private ProjectGroup mockProjectGroup(Long projectGroupId) {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("TeamPo")
			.projectTitle("팀포")
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ReflectionTestUtils.setField(projectGroup, "id", projectGroupId);
		return projectGroup;
	}

	private ChatRoom mockChatRoom(Long chatRoomId, ProjectGroup projectGroup) {
		ChatRoom chatRoom = ChatRoom.builder()
			.projectGroup(projectGroup)
			.build();
		ReflectionTestUtils.setField(chatRoom, "id", chatRoomId);
		return chatRoom;
	}
}

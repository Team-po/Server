package team.po.feature.chat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.chat.domain.ChatRoom;
import team.po.feature.chat.repository.ChatRoomRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

	private final ChatRoomRepository chatRoomRepository;
	private final ProjectGroupRepository projectGroupRepository;

	@Transactional
	public ChatRoom getOrCreateRoom(Long projectGroupId) {
		return chatRoomRepository.findByProjectGroup_Id(projectGroupId)
			.orElseGet(() -> createRoomWithProjectGroupLock(projectGroupId));
	}

	@Transactional
	public ChatRoom getOrCreateRoomForUpdate(Long projectGroupId) {
		ProjectGroup projectGroup = getProjectGroupForUpdate(projectGroupId);
		return chatRoomRepository.findByProjectGroup_Id(projectGroupId)
			.orElseGet(() -> chatRoomRepository.save(ChatRoom.builder()
				.projectGroup(projectGroup)
				.build()));
	}

	@Transactional
	public void createRoomIfAbsent(Long projectGroupId) {
		getOrCreateRoom(projectGroupId);
	}

	private ChatRoom createRoomWithProjectGroupLock(Long projectGroupId) {
		ProjectGroup projectGroup = getProjectGroupForUpdate(projectGroupId);
		return chatRoomRepository.findByProjectGroup_Id(projectGroupId)
			.orElseGet(() -> chatRoomRepository.save(ChatRoom.builder()
				.projectGroup(projectGroup)
				.build()));
	}

	private ProjectGroup getProjectGroupForUpdate(Long projectGroupId) {
		return projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));
	}
}

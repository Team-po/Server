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
			.orElseGet(() -> createRoom(projectGroupId));
	}

	@Transactional
	public void createRoomIfAbsent(Long projectGroupId) {
		if (chatRoomRepository.existsByProjectGroup_Id(projectGroupId)) {
			return;
		}

		createRoom(projectGroupId);
	}

	private ChatRoom createRoom(Long projectGroupId) {
		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));
		return chatRoomRepository.save(new ChatRoom(projectGroup));
	}
}

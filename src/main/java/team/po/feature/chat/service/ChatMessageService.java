package team.po.feature.chat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
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
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

	private static final int MAX_CONTENT_LENGTH = 2000;
	private static final int DEFAULT_PAGE_SIZE = 30;
	private static final int MAX_PAGE_SIZE = 50;

	private final ChatRoomService chatRoomService;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatReadStateRepository chatReadStateRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ProjectGroupRepository projectGroupRepository;

	@Transactional
	public ChatMessageResponse sendMessage(Long projectGroupId, Long requesterUserId, SendChatMessageRequest request) {
		ProjectGroupMember membership = getRequesterMembership(projectGroupId, requesterUserId);
		String content = normalizeContent(request.content());
		ProjectGroup projectGroup = getProjectGroupForUpdate(projectGroupId);
		assertWritable(projectGroup);

		ChatRoom chatRoom = chatRoomService.getOrCreateRoom(projectGroupId);
		ChatMessage savedMessage = chatMessageRepository.save(
			ChatMessage.builder()
				.chatRoom(chatRoom)
				.sender(membership.getUser())
				.type(ChatMessageType.TEXT)
				.content(content)
				.build()
		);

		return ChatMessageResponse.from(savedMessage, requesterUserId);
	}

	@Transactional
	public ChatMessagePageResponse getMessages(
		Long projectGroupId,
		Long requesterUserId,
		Long beforeMessageId,
		Integer size
	) {
		getRequesterMembership(projectGroupId, requesterUserId);
		ChatRoom chatRoom = chatRoomService.getOrCreateRoom(projectGroupId);
		int normalizedSize = normalizePageSize(size);

		List<ChatMessage> fetchedMessages = chatMessageRepository.findLatestMessages(
			chatRoom.getId(),
			beforeMessageId,
			PageRequest.of(0, normalizedSize + 1)
		);
		boolean hasNext = fetchedMessages.size() > normalizedSize;
		List<ChatMessage> pageMessages = new ArrayList<>(
			fetchedMessages.subList(0, Math.min(normalizedSize, fetchedMessages.size()))
		);
		Collections.reverse(pageMessages);

		List<ChatMessageResponse> responses = pageMessages.stream()
			.map(message -> ChatMessageResponse.from(message, requesterUserId))
			.toList();
		Long nextBeforeMessageId = hasNext && !pageMessages.isEmpty()
			? pageMessages.get(0).getId()
			: null;

		return new ChatMessagePageResponse(responses, nextBeforeMessageId, hasNext);
	}

	@Transactional
	public ChatReadStateResponse markRead(Long projectGroupId, Long requesterUserId, MarkChatReadRequest request) {
		ProjectGroupMember membership = getRequesterMembership(projectGroupId, requesterUserId);
		ChatRoom chatRoom = chatRoomService.getOrCreateRoomForUpdate(projectGroupId);
		ChatMessage message = chatMessageRepository
			.findByIdAndChatRoom_Id(request.lastReadMessageId(), chatRoom.getId())
			.orElseThrow(() -> new ApplicationException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));

		Optional<ChatReadState> existingReadState = chatReadStateRepository
			.findByChatRoom_IdAndUser_Id(chatRoom.getId(), requesterUserId);
		ChatReadState readState = existingReadState
			.orElseGet(() -> ChatReadState.builder()
				.chatRoom(chatRoom)
				.user(membership.getUser())
				.build());
		readState.markRead(message);

		if (existingReadState.isEmpty()) {
			chatReadStateRepository.save(readState);
		}

		return ChatReadStateResponse.from(readState);
	}

	private ProjectGroupMember getRequesterMembership(Long projectGroupId, Long requesterUserId) {
		return projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(projectGroupId, requesterUserId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED));
	}

	private ProjectGroup getProjectGroupForUpdate(Long projectGroupId) {
		return projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));
	}

	private void assertWritable(ProjectGroup projectGroup) {
		if (projectGroup.getStatus() == ProjectGroupStatus.FINISHED) {
			throw new ApplicationException(ErrorCode.CHAT_MESSAGE_WRITE_NOT_ALLOWED);
		}
	}

	private String normalizeContent(String content) {
		if (!StringUtils.hasText(content)) {
			throw new ApplicationException(ErrorCode.CHAT_MESSAGE_CONTENT_REQUIRED);
		}

		String trimmedContent = content.trim();
		if (trimmedContent.length() > MAX_CONTENT_LENGTH) {
			throw new ApplicationException(ErrorCode.CHAT_MESSAGE_CONTENT_TOO_LONG);
		}

		return trimmedContent;
	}

	private int normalizePageSize(Integer size) {
		if (size == null || size <= 0) {
			return DEFAULT_PAGE_SIZE;
		}

		return Math.min(size, MAX_PAGE_SIZE);
	}
}

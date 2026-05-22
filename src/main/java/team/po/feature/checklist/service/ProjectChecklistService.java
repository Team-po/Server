package team.po.feature.checklist.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.common.ai.client.GeminiClient;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.checklist.ai.ChecklistAdvicePromptBuilder;
import team.po.feature.checklist.ai.ChecklistAdviceSchema;
import team.po.feature.checklist.domain.ProjectChecklist;
import team.po.feature.checklist.domain.ProjectChecklistStatus;
import team.po.feature.checklist.dto.ChecklistAiAdviceResponse;
import team.po.feature.checklist.dto.CreateProjectChecklistRequest;
import team.po.feature.checklist.dto.GenerateChecklistAdviceResponse;
import team.po.feature.checklist.dto.ProjectChecklistResponse;
import team.po.feature.checklist.dto.UpdateProjectChecklistRequest;
import team.po.feature.checklist.repository.ProjectChecklistRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.user.domain.Users;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectChecklistService {

	private final ProjectChecklistRepository projectChecklistRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ChecklistAdvicePromptBuilder checklistAdvicePromptBuilder;
	private final GeminiClient geminiClient;
	private final ObjectMapper objectMapper;

	// 체크리스트 목록 조회
	@Transactional(readOnly = true)
	public List<ProjectChecklistResponse> getProjectChecklists(Long projectGroupId, Users requester) {
		this.getRequesterMembership(projectGroupId, requester.getId());

		return projectChecklistRepository.findAllByProjectGroup_IdOrderByIdAsc(projectGroupId).stream()
			.map(this::toResponse)
			.toList();
	}

	//체크리스트 생성
	@Transactional
	public ProjectChecklistResponse createProjectChecklist(
		Long projectGroupId,
		Users requester,
		CreateProjectChecklistRequest request
	) {
		ProjectGroupMember membership = this.getRequesterMembership(projectGroupId, requester.getId());
		this.assertChecklistWritable(membership.getProjectGroup());

		ProjectChecklist savedChecklist = projectChecklistRepository.save(ProjectChecklist.builder()
			.projectGroup(membership.getProjectGroup())
			.title(this.normalizeTitle(request.title()))
			.description(this.normalizeDescription(request.description()))
			.status(ProjectChecklistStatus.TODO)
			.dueDate(request.dueDate())
			.assignee(this.resolveAssignee(projectGroupId, request.assigneeUserId()))
			.createdBy(requester)
			.build());

		return this.toResponse(savedChecklist);
	}

	//체크리스트 수정
	@Transactional
	public ProjectChecklistResponse updateProjectChecklist(
		Long projectGroupId,
		Long checklistId,
		Users requester,
		UpdateProjectChecklistRequest request
	) {
		this.getRequesterMembership(projectGroupId, requester.getId());
		ProjectChecklist checklist = this.getChecklist(projectGroupId, checklistId);
		this.assertChecklistWritable(checklist.getProjectGroup());

		checklist.update(
			this.normalizeTitle(request.title()),
			this.normalizeDescription(request.description()),
			request.status(),
			request.dueDate(),
			this.resolveAssignee(projectGroupId, request.assigneeUserId())
		);

		return this.toResponse(checklist);
	}

	//체크리스트 삭제
	@Transactional
	public void deleteProjectChecklist(Long projectGroupId, Long checklistId, Users requester) {
		this.getRequesterMembership(projectGroupId, requester.getId());
		ProjectChecklist checklist = this.getChecklist(projectGroupId, checklistId);
		this.assertChecklistWritable(checklist.getProjectGroup());
		projectChecklistRepository.delete(checklist);
	}

	@Transactional
	public GenerateChecklistAdviceResponse generateChecklistAdvice(Long projectGroupId, Long checklistId,
		Users requester) {
		this.getRequesterMembership(projectGroupId, requester.getId());
		ProjectChecklist checklist = this.getChecklist(projectGroupId, checklistId);
		this.assertChecklistWritable(checklist.getProjectGroup());

		if (!StringUtils.hasText(checklist.getDescription())) {
			throw new ApplicationException(ErrorCode.PROJECT_CHECKLIST_DESCRIPTION_REQUIRED_FOR_AI);
		}

		String prompt = checklistAdvicePromptBuilder.build(
			checklist.getTitle(),
			checklist.getDescription(),
			checklist.getDueDate()
		);
		String adviceJson = geminiClient.generateStructuredJson(prompt, ChecklistAdviceSchema.RESPONSE_SCHEMA);
		ChecklistAiAdviceResponse aiAdvice = this.parseGeneratedAdvice(adviceJson, checklist.getId());
		checklist.updateAiAdvice(this.serializeAdvice(aiAdvice, checklist.getId()));

		return new GenerateChecklistAdviceResponse(checklist.getId(), aiAdvice);
	}

	private ProjectGroupMember getRequesterMembership(Long projectGroupId, Long requesterUserId) {
		return projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(projectGroupId, requesterUserId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED));
	}

	private ProjectChecklist getChecklist(Long projectGroupId, Long checklistId) {
		return projectChecklistRepository.findByIdAndProjectGroup_Id(checklistId, projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_CHECKLIST_NOT_FOUND));
	}

	private Users resolveAssignee(Long projectGroupId, Long assigneeUserId) {
		if (assigneeUserId == null) {
			return null;
		}

		return projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(projectGroupId, assigneeUserId)
			.map(ProjectGroupMember::getUser)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_CHECKLIST_ASSIGNEE_NOT_MEMBER));
	}

	private void assertChecklistWritable(ProjectGroup projectGroup) {
		if (projectGroup.getStatus() == ProjectGroupStatus.FINISHED) {
			throw new ApplicationException(ErrorCode.PROJECT_CHECKLIST_WRITE_NOT_ALLOWED);
		}
	}

	private String normalizeDescription(String description) {
		if (description == null) {
			return null;
		}

		String trimmed = description.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeTitle(String title) {
		if (!StringUtils.hasText(title)) {
			throw new ApplicationException(ErrorCode.PROJECT_CHECKLIST_TITLE_REQUIRED);
		}

		return title.trim();
	}

	private ChecklistAiAdviceResponse parseGeneratedAdvice(String adviceJson, Long checklistId) {
		try {
			return parseAndValidateAdvice(adviceJson);
		} catch (IOException exception) {
			log.error("체크리스트 AI 조언 JSON 파싱 실패: checklistId={}, jsonLength={}", checklistId, adviceJson.length(),
				exception);
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
	}

	private String serializeAdvice(ChecklistAiAdviceResponse aiAdvice, Long checklistId) {
		try {
			return objectMapper.writeValueAsString(aiAdvice);
		} catch (IOException exception) {
			log.error("체크리스트 AI 조언 JSON 직렬화 실패: checklistId={}", checklistId, exception);
			throw new ApplicationException(ErrorCode.PROJECT_CHECKLIST_DATA_ERROR);
		}
	}

	private ChecklistAiAdviceResponse deserializeAdvice(ProjectChecklist checklist) {
		if (!StringUtils.hasText(checklist.getAiAdvice())) {
			return null;
		}

		try {
			return parseAndValidateAdvice(checklist.getAiAdvice());
		} catch (IOException exception) {
			log.error(
				"저장된 체크리스트 AI 조언 JSON 파싱 실패: checklistId={}, jsonLength={}",
				checklist.getId(),
				checklist.getAiAdvice().length(),
				exception
			);
			throw new ApplicationException(ErrorCode.PROJECT_CHECKLIST_DATA_ERROR);
		}
	}

	private ChecklistAiAdviceResponse parseAndValidateAdvice(String adviceJson) throws IOException {
		ChecklistAiAdviceResponse aiAdvice = objectMapper.readValue(adviceJson, ChecklistAiAdviceResponse.class);
		aiAdvice.validate();
		return aiAdvice;
	}

	private ProjectChecklistResponse toResponse(ProjectChecklist checklist) {
		return new ProjectChecklistResponse(
			checklist.getId(),
			checklist.getTitle(),
			checklist.getDescription(),
			checklist.getStatus(),
			checklist.getDueDate(),
			checklist.getAssignee() == null ? null : checklist.getAssignee().getId(),
			checklist.getAssignee() == null ? "ALL" : checklist.getAssignee().getNickname(),
			this.deserializeAdvice(checklist)
		);
	}
}

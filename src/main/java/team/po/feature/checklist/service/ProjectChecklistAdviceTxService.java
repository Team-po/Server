package team.po.feature.checklist.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.checklist.ai.ChecklistAdvicePromptBuilder;
import team.po.feature.checklist.domain.ProjectChecklist;
import team.po.feature.checklist.repository.ProjectChecklistRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;

@Service
@RequiredArgsConstructor
public class ProjectChecklistAdviceTxService {

	private final ProjectChecklistRepository projectChecklistRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ChecklistAdvicePromptBuilder checklistAdvicePromptBuilder;

	@Transactional(readOnly = true)
	public AdviceGenerationContext prepareChecklistAdviceGeneration(
		Long projectGroupId,
		Long checklistId,
		Long requesterUserId
	) {
		this.getRequesterMembership(projectGroupId, requesterUserId);
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
		return new AdviceGenerationContext(checklist.getId(), prompt);
	}

	@Transactional
	public void persistChecklistAdvice(
		Long projectGroupId,
		Long checklistId,
		Long requesterUserId,
		String serializedAdvice
	) {
		this.getRequesterMembership(projectGroupId, requesterUserId);
		ProjectChecklist checklist = this.getChecklist(projectGroupId, checklistId);
		this.assertChecklistWritable(checklist.getProjectGroup());

		if (!StringUtils.hasText(checklist.getDescription())) {
			throw new ApplicationException(ErrorCode.PROJECT_CHECKLIST_DESCRIPTION_REQUIRED_FOR_AI);
		}

		checklist.updateAiAdvice(serializedAdvice);
	}

	private ProjectGroupMember getRequesterMembership(Long projectGroupId, Long requesterUserId) {
		return projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(projectGroupId, requesterUserId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED));
	}

	private ProjectChecklist getChecklist(Long projectGroupId, Long checklistId) {
		return projectChecklistRepository.findByIdAndProjectGroup_Id(checklistId, projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_CHECKLIST_NOT_FOUND));
	}

	private void assertChecklistWritable(ProjectGroup projectGroup) {
		if (projectGroup.getStatus() == ProjectGroupStatus.FINISHED) {
			throw new ApplicationException(ErrorCode.PROJECT_CHECKLIST_WRITE_NOT_ALLOWED);
		}
	}

	public record AdviceGenerationContext(Long checklistId, String prompt) {
	}
}

package team.po.feature.teamrule.service;

import java.util.Objects;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;
import team.po.feature.teamrule.domain.ProjectTeamRule;
import team.po.feature.teamrule.dto.TeamRuleResponse;
import team.po.feature.teamrule.dto.UpdateTeamRuleRequest;
import team.po.feature.teamrule.repository.ProjectTeamRuleRepository;
import team.po.feature.user.domain.Users;

@Service
@RequiredArgsConstructor
public class TeamRuleService {
	private final ProjectTeamRuleRepository projectTeamRuleRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ProjectGroupRepository projectGroupRepository;

	@Transactional
	public TeamRuleResponse getTeamRule(Long projectGroupId, Users requester) {
		getRequesterMembership(projectGroupId, requester.getId());

		ProjectTeamRule teamRule = projectTeamRuleRepository.findByProjectGroup_Id(projectGroupId)
			.orElseGet(() -> createDefaultTeamRule(projectGroupId, requester));

		return TeamRuleResponse.from(teamRule);
	}

	@Transactional
	public TeamRuleResponse updateTeamRule(Long projectGroupId, Users requester, UpdateTeamRuleRequest request) {
		ProjectGroupMember membership = getRequesterMembership(projectGroupId, requester.getId());
		assertTeamRuleWritable(membership.getProjectGroup());

		ProjectTeamRule teamRule = projectTeamRuleRepository.findByProjectGroup_Id(projectGroupId)
			.orElseGet(() -> createDefaultTeamRule(projectGroupId, requester));

		assertVersionMatches(teamRule, request.version());
		teamRule.update(normalizeContent(request.content()), requester);

		try {
			projectTeamRuleRepository.flush();
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
			throw new ApplicationException(ErrorCode.PROJECT_TEAM_RULE_UPDATE_CONFLICT, exception);
		}

		return TeamRuleResponse.from(teamRule);
	}

	private ProjectTeamRule createDefaultTeamRule(Long projectGroupId, Users requester) {
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		return projectTeamRuleRepository.findByProjectGroup_Id(projectGroupId)
			.orElseGet(() -> projectTeamRuleRepository.saveAndFlush(
				ProjectTeamRule.createDefault(projectGroup, requester)
			));
	}

	private ProjectGroupMember getRequesterMembership(Long projectGroupId, Long requesterUserId) {
		return projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(projectGroupId, requesterUserId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED));
	}

	private void assertTeamRuleWritable(ProjectGroup projectGroup) {
		if (projectGroup.getStatus() == ProjectGroupStatus.FINISHED) {
			throw new ApplicationException(ErrorCode.PROJECT_TEAM_RULE_WRITE_NOT_ALLOWED);
		}
	}

	private void assertVersionMatches(ProjectTeamRule teamRule, Long requestVersion) {
		if (!Objects.equals(teamRule.getVersion(), requestVersion)) {
			throw new ApplicationException(ErrorCode.PROJECT_TEAM_RULE_UPDATE_CONFLICT);
		}
	}

	private String normalizeContent(String content) {
		if (!StringUtils.hasText(content)) {
			throw new ApplicationException(ErrorCode.PROJECT_TEAM_RULE_CONTENT_REQUIRED);
		}

		return content;
	}
}

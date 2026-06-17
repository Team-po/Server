package team.po.feature.teamrule.dto;

import java.time.Instant;

import team.po.feature.teamrule.domain.ProjectTeamRule;

public record TeamRuleResponse(
	Long id,
	Long projectGroupId,
	String content,
	Long version,
	Instant updatedAt,
	String updatedByNickname
) {
	public static TeamRuleResponse from(ProjectTeamRule teamRule) {
		return new TeamRuleResponse(
			teamRule.getId(),
			teamRule.getProjectGroup().getId(),
			teamRule.getContent(),
			teamRule.getVersion(),
			teamRule.getUpdatedAt(),
			teamRule.getUpdatedBy().getNickname()
		);
	}
}

package team.po.feature.teamspace.service;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyGithubSummaryScheduler {
	private static final String WEEKLY_GITHUB_SUMMARY_CRON = "0 0 4 * * MON";
	private static final String WEEKLY_GITHUB_SUMMARY_ZONE = "Asia/Seoul";

	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final TeamspaceService teamspaceService;

	@Scheduled(cron = WEEKLY_GITHUB_SUMMARY_CRON, zone = WEEKLY_GITHUB_SUMMARY_ZONE)
	public void generateWeeklyGithubSummaries() {
		String cycleId = UUID.randomUUID().toString().substring(0, 8);
		log.info("[{}] Github 주간 요약 스케줄 시작", cycleId);

		List<ProjectGroupMember> targetMembers = projectGroupMemberRepository
			.findWeeklyGithubSummaryTargetMembers(ProjectGroupStatus.ACTIVE);
		int successCount = 0;
		int failureCount = 0;

		for (ProjectGroupMember member : targetMembers) {
			Long projectGroupId = member.getProjectGroup().getId();
			Long projectGroupMemberId = member.getId();
			Long userId = member.getUser().getId();

			try {
				teamspaceService.generateWeeklyGithubSummary(member.getUser(), projectGroupId);
				successCount++;
			} catch (Exception exception) {
				failureCount++;
				log.error(
					"[{}] Github 주간 요약 생성 실패: projectGroupId={}, projectGroupMemberId={}, userId={}",
					cycleId,
					projectGroupId,
					projectGroupMemberId,
					userId,
					exception
				);
			}
		}

		log.info(
			"[{}] Github 주간 요약 스케줄 완료: targetCount={}, successCount={}, failureCount={}",
			cycleId,
			targetMembers.size(),
			successCount,
			failureCount
		);
	}
}

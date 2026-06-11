package team.po.feature.teamspace.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;

@Slf4j
@Component
public class WeeklyGithubSummaryScheduler {
	private static final String WEEKLY_GITHUB_SUMMARY_CRON = "0 0 4 * * MON";
	private static final String WEEKLY_GITHUB_SUMMARY_ZONE = "Asia/Seoul";
	private static final List<Duration> DEFAULT_RETRY_BACKOFFS = List.of(
		Duration.ofSeconds(10),
		Duration.ofSeconds(30)
	);

	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final TeamspaceService teamspaceService;
	private final List<Duration> retryBackoffs;
	private final BackoffSleeper backoffSleeper;

	@Autowired
	public WeeklyGithubSummaryScheduler(
		ProjectGroupMemberRepository projectGroupMemberRepository,
		TeamspaceService teamspaceService
	) {
		this(projectGroupMemberRepository, teamspaceService, DEFAULT_RETRY_BACKOFFS, WeeklyGithubSummaryScheduler::sleep);
	}

	WeeklyGithubSummaryScheduler(
		ProjectGroupMemberRepository projectGroupMemberRepository,
		TeamspaceService teamspaceService,
		List<Duration> retryBackoffs,
		BackoffSleeper backoffSleeper
	) {
		this.projectGroupMemberRepository = projectGroupMemberRepository;
		this.teamspaceService = teamspaceService;
		this.retryBackoffs = retryBackoffs;
		this.backoffSleeper = backoffSleeper;
	}

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

			if (generateWeeklyGithubSummaryWithRetry(member, cycleId, projectGroupId, projectGroupMemberId, userId)) {
				successCount++;
			} else {
				failureCount++;
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

	private boolean generateWeeklyGithubSummaryWithRetry(
		ProjectGroupMember member,
		String cycleId,
		Long projectGroupId,
		Long projectGroupMemberId,
		Long userId
	) {
		int maxAttempts = retryBackoffs.size() + 1;
		int attempt = 1;
		while (true) {
			try {
				teamspaceService.generateWeeklyGithubSummary(member.getUser(), projectGroupId);
				if (attempt > 1) {
					log.info(
						"[{}] Github 주간 요약 재시도 성공: projectGroupId={}, projectGroupMemberId={}, userId={}, attempt={}",
						cycleId,
						projectGroupId,
						projectGroupMemberId,
						userId,
						attempt
					);
				}
				return true;
			} catch (Exception exception) {
				boolean retryable = isRetryable(exception);
				if (!retryable || attempt >= maxAttempts) {
					log.error(
						"[{}] Github 주간 요약 생성 실패: projectGroupId={}, projectGroupMemberId={}, userId={}, attempt={}, retryable={}",
						cycleId,
						projectGroupId,
						projectGroupMemberId,
						userId,
						attempt,
						retryable,
						exception
					);
					return false;
				}

				Duration backoff = retryBackoffs.get(attempt - 1);
				log.warn(
					"[{}] Github 주간 요약 생성 재시도 예정: projectGroupId={}, projectGroupMemberId={}, userId={}, attempt={}, nextAttempt={}, maxAttempts={}, backoffMillis={}, errorCode={}",
					cycleId,
					projectGroupId,
					projectGroupMemberId,
					userId,
					attempt,
					attempt + 1,
					maxAttempts,
					backoff.toMillis(),
					extractErrorCode(exception)
				);

				if (!sleepBeforeRetry(cycleId, projectGroupId, projectGroupMemberId, userId, backoff)) {
					return false;
				}
				attempt++;
			}
		}
	}

	private boolean isRetryable(Exception exception) {
		if (!(exception instanceof ApplicationException applicationException)) {
			return false;
		}

		ErrorCode errorCode = applicationException.getErrorCode();
		return errorCode == ErrorCode.GEMINI_API_ERROR
			|| errorCode == ErrorCode.GEMINI_INVALID_RESPONSE
			|| errorCode == ErrorCode.GITHUB_API_REQUEST_FAILED;
	}

	private String extractErrorCode(Exception exception) {
		if (exception instanceof ApplicationException applicationException) {
			return applicationException.getCode();
		}
		return exception.getClass().getSimpleName();
	}

	private boolean sleepBeforeRetry(
		String cycleId,
		Long projectGroupId,
		Long projectGroupMemberId,
		Long userId,
		Duration backoff
	) {
		try {
			backoffSleeper.sleep(backoff);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.error(
				"[{}] Github 주간 요약 재시도 대기 중 인터럽트 발생: projectGroupId={}, projectGroupMemberId={}, userId={}",
				cycleId,
				projectGroupId,
				projectGroupMemberId,
				userId,
				exception
			);
			return false;
		}
	}

	private static void sleep(Duration duration) throws InterruptedException {
		Thread.sleep(duration.toMillis());
	}

	@FunctionalInterface
	interface BackoffSleeper {
		void sleep(Duration duration) throws InterruptedException;
	}
}

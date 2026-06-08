package team.po.feature.teamspace.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.WeeklyGithubSummary;

public interface WeeklyGithubSummaryRepository extends JpaRepository<WeeklyGithubSummary, Long> {
	Optional<WeeklyGithubSummary> findByProjectGroupMember_IdAndPeriodStartAndPeriodEnd(
		Long projectGroupMemberId,
		Instant periodStart,
		Instant periodEnd
	);
}

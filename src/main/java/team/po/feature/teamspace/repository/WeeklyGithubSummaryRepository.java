package team.po.feature.teamspace.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.WeeklyGithubSummary;

public interface WeeklyGithubSummaryRepository extends JpaRepository<WeeklyGithubSummary, Long> {
	Optional<WeeklyGithubSummary> findByProjectGroupMember_IdAndPeriodStartAndPeriodEnd(
		Long projectGroupMemberId,
		Instant periodStart,
		Instant periodEnd
	);

	List<WeeklyGithubSummary> findAllByProjectGroupMember_IdOrderByPeriodEndDesc(Long projectGroupMemberId);
}

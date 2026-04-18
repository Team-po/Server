package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import team.po.feature.match.domain.MatchingSession;

public interface MatchingSessionRepository extends JpaRepository<MatchingSession, Long> {
	Optional<MatchingSession> findByIdAndDeletedAtIsNull(Long matchId);

	// 빈 자리가 있는 모든 활성 세션 조회
	@Query("SELECT ms FROM MatchingSession ms WHERE ms.deletedAt IS NULL")
	List<MatchingSession> findAllActive();
}
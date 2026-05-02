package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import team.po.feature.match.domain.MatchingSession;

public interface MatchingSessionRepository extends JpaRepository<MatchingSession, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT ms FROM MatchingSession ms
			WHERE ms.id = :id
				AND ms.deletedAt IS NULL
		""")
	Optional<MatchingSession> findByIdWithLock(@Param("id") Long id);

	// 빈 자리가 있는 모든 활성 세션 조회
	@Query("SELECT ms FROM MatchingSession ms WHERE ms.deletedAt IS NULL")
	List<MatchingSession> findAllActive();

	Optional<MatchingSession> findByIdAndDeletedAtIsNull(Long id);
}
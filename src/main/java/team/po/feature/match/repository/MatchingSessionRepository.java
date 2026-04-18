package team.po.feature.match.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.match.domain.MatchingSession;

public interface MatchingSessionRepository extends JpaRepository<MatchingSession, Long> {
	Optional<MatchingSession> findByIdAndDeletedAtIsNull(Long matchId);
}
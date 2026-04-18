package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.match.domain.MatchingMember;

public interface MatchingMemberRepository extends JpaRepository<MatchingMember, Long> {
	Optional<MatchingMember> findByUserIdAndIsAcceptedIsNullAndDeletedAtIsNull(Long id);

	@Query("SELECT mm FROM MatchingMember mm " +
		"WHERE mm.matchingSessionId = :matchId AND mm.deletedAt IS NULL")
	List<MatchingMember> findAllByMatchingSessionId(@Param("matchId") Long matchId);

}

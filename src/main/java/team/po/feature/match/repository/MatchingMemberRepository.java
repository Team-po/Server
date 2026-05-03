package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.match.domain.MatchingMember;

public interface MatchingMemberRepository extends JpaRepository<MatchingMember, Long> {
	// 매칭 세션 내 모든 활성 멤버와 연관 엔티티 조회
	@Query("""
		SELECT mm FROM MatchingMember mm
		JOIN FETCH mm.projectRequest pr
		JOIN FETCH pr.user
		WHERE mm.matchingSession.id = :matchId 
		  AND mm.deletedAt IS NULL
		""")
	List<MatchingMember> findAllActiveBySessionIdWithFetch(@Param("matchId") Long matchId);

	// 특정 세션에서 거절한 유저 ID 목록 조회
	@Query("""
		SELECT mm.projectRequest.user.id FROM MatchingMember mm
		WHERE mm.matchingSession.id = :sessionId
		  AND mm.isAccepted = false
		""")
	List<Long> findRejectedUserIdsBySessionId(@Param("sessionId") Long sessionId);

	// 전원 수락 여부 확인
	@Query("""
		SELECT COUNT(mm) = :teamSize FROM MatchingMember mm
		WHERE mm.matchingSession.id = :sessionId
		  AND mm.deletedAt IS NULL
		  AND mm.isAccepted = true
		""")
	boolean isAllAccepted(@Param("sessionId") Long sessionId, @Param("teamSize") int teamSize);

	@Query("""
		SELECT mm FROM MatchingMember mm
		WHERE mm.projectRequest.user.id = :userId
		  AND mm.deletedAt IS NULL
		""")
	Optional<MatchingMember> findCurrentActiveByUserId(@Param("userId") Long userId);
}

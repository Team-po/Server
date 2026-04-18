package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.match.domain.MatchingMember;

public interface MatchingMemberRepository extends JpaRepository<MatchingMember, Long> {
	@Query("SELECT mm FROM MatchingMember mm " +
		"WHERE mm.matchingSessionId = :matchId AND mm.deletedAt IS NULL")
	List<MatchingMember> findAllByMatchingSessionId(@Param("matchId") Long matchId);

	// 특정 세션에서 거절한 유저 ID 목록 조회
	@Query("""
		SELECT mm.userId FROM MatchingMember mm
		WHERE mm.matchingSessionId = :sessionId
		  AND mm.isAccepted = false
		  AND mm.deletedAt IS NULL
		""")
	List<Long> findRejectedUserIdsBySessionId(@Param("sessionId") Long sessionId);

	// 특정 세션에서 유효한 멤버 수 (매칭 취소 또는 거절하지 않은 멤버)
	@Query("""
		SELECT COUNT(mm) FROM MatchingMember mm
		WHERE mm.matchingSessionId = :sessionId
		  AND mm.deletedAt IS NULL
		  AND (mm.isAccepted IS NULL OR mm.isAccepted = true)
		""")
	int countActiveValidBySessionId(@Param("sessionId") Long sessionId);

	// 특정 세션의 유효 멤버 리스트 조회 (빈 포지션 확인)
	@Query("""
		SELECT mm FROM MatchingMember mm
		WHERE mm.matchingSessionId = :sessionId
		  AND mm.deletedAt IS NULL
		  AND (mm.isAccepted IS NULL OR mm.isAccepted = true)
		""")
	List<MatchingMember> findActiveValidMembersBySessionId(@Param("sessionId") Long sessionId);

	// Session ID + User ID로 해당 멤버 조회
	@Query("""
		SELECT mm FROM MatchingMember mm
		WHERE mm.matchingSessionId = :sessionId
		  AND mm.userId = :userId
		  AND mm.deletedAt IS NULL
		""")
	Optional<MatchingMember> findActiveBySessionIdAndUserId(
		@Param("sessionId") Long sessionId,
		@Param("userId") Long userId
	);

	// 전원 수락 여부 확인
	@Query("""
		SELECT COUNT(mm) = :teamSize FROM MatchingMember mm
		WHERE mm.matchingSessionId = :sessionId
		  AND mm.deletedAt IS NULL
		  AND mm.isAccepted = true
		""")
	boolean isAllAccepted(@Param("sessionId") Long sessionId, @Param("teamSize") int teamSize);

	@Query("""
		SELECT mm FROM MatchingMember mm
		WHERE mm.userId = :userId
		  AND mm.deletedAt IS NULL
		""")
	Optional<MatchingMember> findActiveByUserId(@Param("userId") Long userId);
}

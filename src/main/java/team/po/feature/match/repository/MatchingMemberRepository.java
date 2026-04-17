package team.po.feature.match.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.match.domain.MatchingMember;

public interface MatchingMemberRepository extends JpaRepository<MatchingMember, Long> {
	Optional<MatchingMember> findByUserIdAndIsAcceptedIsNullAndDeletedAtIsNull(Long id);
}

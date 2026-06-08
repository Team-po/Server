package team.po.feature.projectgroup.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;

public interface ProjectGroupMemberRepository extends JpaRepository<ProjectGroupMember, Long> {
	boolean existsByUser_IdInAndProjectGroup_Status(List<Long> userIds, ProjectGroupStatus status);

	boolean existsByUser_IdAndProjectGroup_Status(Long userId, ProjectGroupStatus status);

	Optional<ProjectGroupMember> findByProjectGroup_IdAndGroupRole(Long projectGroupId, GroupRole groupRole);

	Optional<ProjectGroupMember> findByProjectGroup_IdAndUser_Id(Long projectGroupId, Long userId);

	List<ProjectGroupMember> findAllByProjectGroup_IdOrderByIdAsc(Long projectGroupId);

	Optional<ProjectGroupMember> findByUser_IdAndProjectGroup_Status(Long userId, ProjectGroupStatus status);

	boolean existsByProjectGroup_IdAndUser_Id(Long projectGroupId, Long userId);

	boolean existsByProjectGroup_IdAndUser_IdAndGroupRole(Long projectGroupId, Long userId, GroupRole groupRole);

	@Query("""
		SELECT DISTINCT member
		FROM ProjectGroupMember member
		JOIN FETCH member.user user
		JOIN FETCH member.projectGroup projectGroup
		WHERE projectGroup.status = :projectGroupStatus
			AND user.deletedAt IS NULL
			AND EXISTS (
				SELECT 1
				FROM ProjectGroupGithubInstallation connection
				WHERE connection.projectGroup = projectGroup
					AND connection.deletedAt IS NULL
			)
			AND EXISTS (
				SELECT 1
				FROM ProjectGroupGithubRepository repository
				WHERE repository.projectGroup = projectGroup
					AND repository.deletedAt IS NULL
			)
			AND EXISTS (
				SELECT 1
				FROM GithubAccount githubAccount
				WHERE githubAccount.user = user
					AND githubAccount.deletedAt IS NULL
			)
		ORDER BY projectGroup.id ASC, member.id ASC
		""")
	List<ProjectGroupMember> findWeeklyGithubSummaryTargetMembers(
		@Param("projectGroupStatus") ProjectGroupStatus projectGroupStatus
	);
}

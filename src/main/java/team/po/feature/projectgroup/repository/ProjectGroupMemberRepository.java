package team.po.feature.projectgroup.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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

	long countByProjectGroup_Id(Long projectGroupId);

	long countByProjectGroup_IdAndFinishAgreedTrue(Long projectGroupId);
}

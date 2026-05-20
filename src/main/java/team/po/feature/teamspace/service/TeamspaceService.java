package team.po.feature.teamspace.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamspaceService {

	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;
	private final ProjectGroupGithubRepositoryRepository projectGroupGithubRepository;

	@Transactional(readOnly = true)
	public GetGithubInstallationStatusResponse getGithubInstallationStatus(Long projectGroupId, Long requesterUserId) {
		validateProjectGroupMember(projectGroupId, requesterUserId);

		ProjectGroupGithubInstallation githubConnection = projectGroupGithubInstallationRepository
			.findByProjectGroup_IdAndDeletedAtIsNull(projectGroupId)
			.orElse(null);

		if (githubConnection == null) {
			return GetGithubInstallationStatusResponse.disconnected();
		}

		GithubInstallation installation = githubConnection.getGithubInstallation();
		long repositoryCount = projectGroupGithubRepository.countByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);

		GetGithubInstallationStatusResponse response = GetGithubInstallationStatusResponse.connected(
			installation.getAccountLogin(),
			repositoryCount
		);

		return response;
	}

	private void validateProjectGroupMember(Long projectGroupId, Long requesterUserId) {
		boolean exists = projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(
			projectGroupId,
			requesterUserId
		);

		if (!exists) {
			throw new ApplicationException(
				ErrorCode.PROJECT_GROUP_PERMISSION_DENIED,
				"팀 스페이스 멤버만 GitHub 연동 상태를 조회할 수 있습니다."
			);
		}
	}
}

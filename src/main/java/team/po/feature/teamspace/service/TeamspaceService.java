package team.po.feature.teamspace.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.common.redis.RedisService;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.user.domain.Users;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamspaceService {
	private static final String GITHUB_APP_INSTALLATION_STATE_PREFIX = "github-app-install-state:";
	private static final String GITHUB_APP_INSTALLATION_BASE_URL = "https://github.com/apps";

	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;
	private final ProjectGroupGithubRepositoryRepository projectGroupGithubRepository;
	private final RedisService redisService;

	@Value("${github.app.slug:}")
	private String githubAppSlug;

	@Value("${github.app.installation-state-ttl:PT5M}")
	private Duration githubAppInstallationStateTtl;

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

	public CreateGithubAppInstallationUrlResponse createGithubAppInstallationUrl(Users user, Long projectGroupId) {
		validateProjectGroupHost(projectGroupId, user.getId());

		boolean isAlreadyGithubAppInstallation = projectGroupGithubInstallationRepository
			.existsByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);

		if (isAlreadyGithubAppInstallation) {
			throw new ApplicationException(
				ErrorCode.GITHUB_APP_INSTALLATION_ALREADY_EXISTS,
				"이미 GitHub Organization이 연결된 팀 스페이스입니다."
			);
		}

		String state = UUID.randomUUID().toString();
		String statePayload = createGithubAppInstallationStatePayload(projectGroupId, user.getId(), state);
		redisService.setValue(
			createGithubAppInstallationStateKey(state),
			statePayload,
			githubAppInstallationStateTtl
		);

		String installUrl = UriComponentsBuilder
			.fromUriString(GITHUB_APP_INSTALLATION_BASE_URL)
			.pathSegment(githubAppSlug, "installations", "new")
			.queryParam("state", state)
			.build()
			.toUriString();

		return new CreateGithubAppInstallationUrlResponse(installUrl);
	}

	private String createGithubAppInstallationStatePayload(Long projectGroupId, Long requesterUserId, String nonce) {
		return projectGroupId + ":" + requesterUserId + ":" + Instant.now() + ":" + nonce;
	}

	private String createGithubAppInstallationStateKey(String state) {
		return GITHUB_APP_INSTALLATION_STATE_PREFIX + state;
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

	private void validateProjectGroupHost(Long projectGroupId, Long requesterUserId) {
		boolean exists = projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(
			projectGroupId,
			requesterUserId,
			GroupRole.HOST
		);

		if (!exists) {
			throw new ApplicationException(
				ErrorCode.PROJECT_GROUP_PERMISSION_DENIED,
				"팀 스페이스 호스트만 GitHub Organization 연결을 진행할 수 있습니다."
			);
		}
	}
}

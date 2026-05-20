package team.po.feature.teamspace.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.common.redis.RedisService;
import team.po.config.GithubAppProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.dto.CompleteGithubAppInstallationRequest;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.repository.GithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamspaceService {
	private static final String GITHUB_APP_INSTALLATION_STATE_PREFIX = "github-app-install-state:";
	private static final String GITHUB_APP_INSTALLATION_BASE_URL = "https://github.com/apps";
	private static final String GITHUB_APP_INSTALLATION_STATE_DELIMITER = "|";
	private static final String GITHUB_APP_SETUP_ACTION_INSTALL = "install";

	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;
	private final ProjectGroupGithubRepositoryRepository projectGroupGithubRepositoryRepository;
	private final GithubInstallationRepository githubInstallationRepository;
	private final ProjectGroupRepository projectGroupRepository;
	private final UserRepository userRepository;
	private final RedisService redisService;
	private final GithubAppProperties githubAppProperties;
	private final GithubAppClient githubAppClient;

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
		long repositoryCount = projectGroupGithubRepositoryRepository.countByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);

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
		String statePayload = new GithubAppInstallationState(
			projectGroupId,
			user.getId(),
			Instant.now(),
			state
		).serialize();
		redisService.setValue(
			createGithubAppInstallationStateKey(state),
			statePayload,
			githubAppProperties.installationStateTtl()
		);

		String installUrl = UriComponentsBuilder
			.fromUriString(GITHUB_APP_INSTALLATION_BASE_URL)
			.pathSegment(githubAppProperties.slug(), "installations", "new")
			.queryParam("state", state)
			.build()
			.toUriString();

		return new CreateGithubAppInstallationUrlResponse(installUrl);
	}

	@Transactional
	public void completeGithubAppInstallation(
		CompleteGithubAppInstallationRequest request,
		Long projectGroupId,
		Long requesterUserId
	) {
		validateGithubAppSetupAction(request.setupAction());

		String statePayload = redisService.getAndDeleteStringValue(createGithubAppInstallationStateKey(request.state()));
		GithubAppInstallationState installationState = GithubAppInstallationState.deserialize(statePayload);
		validateGithubAppInstallationState(installationState, projectGroupId, requesterUserId, request.state());

		GithubAppClient.GithubAppInstallationInfo installationInfo = githubAppClient.getInstallation(request.installationId());
		validateGithubAppInstallationAccount(installationInfo);

		validateGithubAppInstallationNotConnected(projectGroupId);
		GithubInstallation githubInstallation = saveGithubInstallation(installationInfo);
		saveProjectGroupGithubInstallation(projectGroupId, requesterUserId, githubInstallation);
	}

	private void validateGithubAppInstallationNotConnected(Long projectGroupId) {
		boolean alreadyConnected = projectGroupGithubInstallationRepository
			.existsByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);
		if (!alreadyConnected) {
			return;
		}

		throw new ApplicationException(
			ErrorCode.GITHUB_APP_INSTALLATION_ALREADY_EXISTS,
			"이미 GitHub Organization이 연결된 팀 스페이스입니다."
		);
	}

	private GithubInstallation saveGithubInstallation(GithubAppClient.GithubAppInstallationInfo installationInfo) {
		return githubInstallationRepository.findByInstallationIdAndDeletedAtIsNull(installationInfo.installationId())
			.map(githubInstallation -> {
				githubInstallation.updateAccount(
					installationInfo.accountId(),
					installationInfo.accountLogin(),
					installationInfo.accountType()
				);
				return githubInstallation;
			})
			.orElseGet(() -> githubInstallationRepository.save(GithubInstallation.builder()
				.installationId(installationInfo.installationId())
				.accountId(installationInfo.accountId())
				.accountLogin(installationInfo.accountLogin())
				.accountType(installationInfo.accountType())
				.build()));
	}

	private void saveProjectGroupGithubInstallation(
		Long projectGroupId,
		Long requesterUserId,
		GithubInstallation githubInstallation
	) {
		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));
		Users connectedBy = userRepository.findByIdAndDeletedAtIsNull(requesterUserId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.UNEXISTED_USER));

		projectGroupGithubInstallationRepository.save(ProjectGroupGithubInstallation.builder()
			.projectGroup(projectGroup)
			.githubInstallation(githubInstallation)
			.connectedBy(connectedBy)
			.build());
	}

	private void validateGithubAppInstallationAccount(GithubAppClient.GithubAppInstallationInfo installationInfo) {
		if (GithubInstallation.ORGANIZATION_ACCOUNT_TYPE.equals(installationInfo.accountType())) {
			return;
		}

		throw new ApplicationException(
			ErrorCode.INVALID_GITHUB_APP_INSTALLATION_ACCOUNT,
			"개인 계정이 아닌 GitHub Organization에 TeamPo GitHub App을 설치해야 합니다."
		);
	}

	private void validateGithubAppSetupAction(String setupAction) {
		if (GITHUB_APP_SETUP_ACTION_INSTALL.equals(setupAction)) {
			return;
		}

		throw new ApplicationException(
			ErrorCode.INVALID_GITHUB_APP_SETUP_ACTION,
			"GitHub App 최초 설치 완료 요청은 install 작업만 허용됩니다."
		);
	}

	private void validateGithubAppInstallationState(
		GithubAppInstallationState installationState,
		Long projectGroupId,
		Long requesterUserId,
		String state
	) {
		if (!installationState.projectGroupId().equals(projectGroupId)
			|| !installationState.requesterUserId().equals(requesterUserId)
			|| !installationState.nonce().equals(state)) {
			throwInvalidGithubAppInstallationState();
		}
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

	private static void throwInvalidGithubAppInstallationState() {
		throw new ApplicationException(ErrorCode.INVALID_GITHUB_APP_INSTALLATION_STATE);
	}

	private record GithubAppInstallationState(
		Long projectGroupId,
		Long requesterUserId,
		Instant issuedAt,
		String nonce
	) {
		private String serialize() {
			return projectGroupId
				+ GITHUB_APP_INSTALLATION_STATE_DELIMITER
				+ requesterUserId
				+ GITHUB_APP_INSTALLATION_STATE_DELIMITER
				+ issuedAt
				+ GITHUB_APP_INSTALLATION_STATE_DELIMITER
				+ nonce;
		}

		private static GithubAppInstallationState deserialize(String value) {
			if (value == null || value.isBlank()) {
				throwInvalidGithubAppInstallationState();
			}

			String[] tokens = value.split("\\|", -1);
			if (tokens.length != 4) {
				throwInvalidGithubAppInstallationState();
			}

			try {
				return new GithubAppInstallationState(
					Long.parseLong(tokens[0]),
					Long.parseLong(tokens[1]),
					Instant.parse(tokens[2]),
					tokens[3]
				);
			} catch (RuntimeException exception) {
				throwInvalidGithubAppInstallationState();
				return null;
			}
		}
	}
}

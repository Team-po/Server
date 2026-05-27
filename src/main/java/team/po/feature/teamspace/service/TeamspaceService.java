package team.po.feature.teamspace.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubRepository;
import team.po.feature.teamspace.dto.CompleteGithubAppInstallationRequest;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.dto.GetAvailiableGithubRepositoryList;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.teamspace.dto.GetGithubRepositoryListResponse;
import team.po.feature.teamspace.dto.SetGithubRepositoryListRequest;
import team.po.feature.teamspace.repository.GithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.user.domain.GithubAccount;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.GithubAccountRepository;
import team.po.feature.user.repository.UserRepository;
import team.po.feature.user.service.GithubTokenEncryptor;

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
	private final GithubAccountRepository githubAccountRepository;
	private final RedisService redisService;
	private final GithubAppProperties githubAppProperties;
	private final GithubAppClient githubAppClient;
	private final GithubTokenEncryptor githubTokenEncryptor;

	@Transactional(readOnly = true)
	public GetGithubInstallationStatusResponse getGithubInstallationStatus(Long projectGroupId, Long requesterUserId) {
		validateProjectGroupMember(projectGroupId, requesterUserId);

		return projectGroupGithubInstallationRepository
			.findByProjectGroup_IdAndDeletedAtIsNull(projectGroupId)
			.map(githubConnection -> {
				GithubInstallation installation = githubConnection.getGithubInstallation();
				long repositoryCount = projectGroupGithubRepositoryRepository
					.countByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);
				return GetGithubInstallationStatusResponse.connected(
					installation.getAccountLogin(),
					repositoryCount
				);
			})
			.orElseGet(GetGithubInstallationStatusResponse::disconnected);
	}

	public CreateGithubAppInstallationUrlResponse createGithubAppInstallationUrl(Users user, Long projectGroupId) {
		validateProjectGroupHost(projectGroupId, user.getId());

		boolean isAlreadyGithubAppInstallation = projectGroupGithubInstallationRepository
			.existsByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);

		if (isAlreadyGithubAppInstallation) {
			throw new ApplicationException(
				ErrorCode.GITHUB_APP_INSTALLATION_ALREADY_EXISTS,
				"이미 Github Organization이 연결된 팀 스페이스입니다."
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

		// Replay 방지를 위해 state는 먼저 소비한다. 이후 처리에 실패하면 기존 state로 재시도하지 않고
		// 설치 URL을 다시 발급받아야 한다.
		String statePayload = redisService.getAndDeleteStringValue(createGithubAppInstallationStateKey(request.state()));
		GithubAppInstallationState installationState = GithubAppInstallationState.deserialize(statePayload);
		validateGithubAppInstallationState(installationState, projectGroupId, requesterUserId, request.state());

		GithubAppClient.GithubAppInstallationInfo installationInfo = githubAppClient.getInstallation(request.installationId());
		validateGithubAppInstallationAccount(installationInfo);
		validateGithubAppInstallationNotConnected(projectGroupId);
		validateRequesterCanConnectOrganization(requesterUserId, installationInfo.accountLogin());

		GithubInstallation githubInstallation = saveGithubInstallation(installationInfo);
		saveProjectGroupGithubInstallation(projectGroupId, requesterUserId, githubInstallation);
	}

	public GetAvailiableGithubRepositoryList getAvailiableGithubRepositoryList(Users user, Long projectGroupId) {
		validateProjectGroupHost(projectGroupId, user.getId());
		ConnectedGithubInstallationIds installation = getConnectedGithubInstallationIds(projectGroupId);

		List<GithubAppClient.GithubRepositoryInfo> repositories = githubAppClient
			.getInstallationRepositories(installation.installationId());

		return new GetAvailiableGithubRepositoryList(repositories.stream()
			.map(repository -> new GetAvailiableGithubRepositoryList.RepositoryResponse(
				repository.githubRepositoryId(),
				repository.repoName(),
				repository.fullName()
			))
			.toList());
	}

	@Transactional(readOnly = true)
	public GetGithubRepositoryListResponse getGithubRepositoryList(Users user, Long projectGroupId) {
		validateProjectGroupMember(projectGroupId, user.getId());

		List<ProjectGroupGithubRepository> repositories = projectGroupGithubRepositoryRepository
			.findAllByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);

		return new GetGithubRepositoryListResponse(repositories.stream()
			.map(repository -> new GetGithubRepositoryListResponse.RepositoryResponse(
				repository.getGithubRepositoryId(),
				repository.getRepoName(),
				repository.getFullName()
			))
			.toList());
	}

	@Transactional
	public void setGithubRepositoryList(Users user, Long projectGroupId, SetGithubRepositoryListRequest request) {
		validateProjectGroupHost(projectGroupId, user.getId());
		ConnectedGithubInstallationIds installation = getConnectedGithubInstallationIds(projectGroupId);

		List<GithubAppClient.GithubRepositoryInfo> repositories = githubAppClient
			.getInstallationRepositories(installation.installationId());

		Map<Long, GithubAppClient.GithubRepositoryInfo> accessibleRepositoryMap = repositories.stream()
			.collect(Collectors.toMap(
				GithubAppClient.GithubRepositoryInfo::githubRepositoryId,
				Function.identity(),
				(first, second) -> first
			));
		Set<Long> selectedGithubRepositoryIds = new LinkedHashSet<>(request.githubRepositoryIds());

		boolean hasInaccessibleRepository = selectedGithubRepositoryIds.stream()
			.anyMatch(repositoryId -> !accessibleRepositoryMap.containsKey(repositoryId));

		if (hasInaccessibleRepository) {
			throw new ApplicationException(
				ErrorCode.GITHUB_REPOSITORY_NOT_ACCESSIBLE,
				"선택한 Github Repository에 접근할 수 없습니다."
			);
		}

		Instant deletedAt = Instant.now();
		List<ProjectGroupGithubRepository> activeRepositories = projectGroupGithubRepositoryRepository
			.findAllByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);
		Map<Long, ProjectGroupGithubRepository> activeRepositoryMap = activeRepositories.stream()
			.collect(Collectors.toMap(
				ProjectGroupGithubRepository::getGithubRepositoryId,
				Function.identity(),
				(first, second) -> first
			));

		activeRepositories.forEach(repository -> {
			Long githubRepositoryId = repository.getGithubRepositoryId();
			if (!selectedGithubRepositoryIds.contains(githubRepositoryId)) {
				repository.softDelete(deletedAt);
				return;
			}

			GithubAppClient.GithubRepositoryInfo latestRepositoryInfo = accessibleRepositoryMap.get(githubRepositoryId);
			repository.updateRepositoryInfo(
				latestRepositoryInfo.owner(),
				latestRepositoryInfo.repoName(),
				latestRepositoryInfo.fullName(),
				latestRepositoryInfo.defaultBranch(),
				latestRepositoryInfo.privateRepository()
			);
		});

		Set<Long> newGithubRepositoryIds = selectedGithubRepositoryIds.stream()
			.filter(repositoryId -> !activeRepositoryMap.containsKey(repositoryId))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		if (newGithubRepositoryIds.isEmpty()) {
			return;
		}

		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));
		GithubInstallation githubInstallation = githubInstallationRepository
			.findByIdAndDeletedAtIsNull(installation.githubInstallationId())
			.orElseThrow(() -> new ApplicationException(
				ErrorCode.GITHUB_APP_INSTALLATION_NOT_CONNECTED,
				"Github Organization이 연결되지 않은 팀 스페이스입니다."
			));

		List<ProjectGroupGithubRepository> selectedRepositories = newGithubRepositoryIds.stream()
			.map(accessibleRepositoryMap::get)
			.map(repository -> ProjectGroupGithubRepository.builder()
				.projectGroup(projectGroup)
				.githubInstallation(githubInstallation)
				.githubRepositoryId(repository.githubRepositoryId())
				.owner(repository.owner())
				.repoName(repository.repoName())
				.fullName(repository.fullName())
				.defaultBranch(repository.defaultBranch())
				.privateRepository(repository.privateRepository())
				.build())
			.toList();

		projectGroupGithubRepositoryRepository.saveAll(selectedRepositories);
	}

	private void validateRequesterCanConnectOrganization(Long requesterUserId, String organizationLogin) {
		GithubAccount githubAccount = githubAccountRepository.findByUserIdAndDeletedAtIsNull(requesterUserId)
			.orElseThrow(() -> new ApplicationException(
				ErrorCode.GITHUB_ACCOUNT_NOT_LINKED,
				"Github Organization 연결을 위해 Github 계정 연동이 필요합니다."
			));
		String accessToken = githubTokenEncryptor.decrypt(githubAccount.getAccessTokenCiphertext());
		if (accessToken == null || accessToken.isBlank()) {
			throw new ApplicationException(
				ErrorCode.GITHUB_ACCOUNT_NOT_LINKED,
				"Github Organization 연결을 위해 Github 계정 연동이 필요합니다."
			);
		}

		githubAppClient.validateOrganizationAdmin(accessToken, organizationLogin);
	}

	private ConnectedGithubInstallationIds getConnectedGithubInstallationIds(Long projectGroupId) {
		ProjectGroupGithubInstallation connection = projectGroupGithubInstallationRepository
			.findByProjectGroup_IdAndDeletedAtIsNull(projectGroupId)
			.orElseThrow(() -> new ApplicationException(
				ErrorCode.GITHUB_APP_INSTALLATION_NOT_CONNECTED,
				"Github Organization이 연결되지 않은 팀 스페이스입니다."
			));

		GithubInstallation installation = connection.getGithubInstallation();
		return new ConnectedGithubInstallationIds(
			installation.getId(),
			installation.getInstallationId()
		);
	}

	private void validateGithubAppInstallationNotConnected(Long projectGroupId) {
		boolean alreadyConnected = projectGroupGithubInstallationRepository
			.existsByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);
		if (!alreadyConnected) {
			return;
		}

		throw new ApplicationException(
			ErrorCode.GITHUB_APP_INSTALLATION_ALREADY_EXISTS,
			"이미 Github Organization이 연결된 팀 스페이스입니다."
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
			"개인 계정이 아닌 Github Organization에 TeamPo Github App을 설치해야 합니다."
		);
	}

	private void validateGithubAppSetupAction(String setupAction) {
		if (GITHUB_APP_SETUP_ACTION_INSTALL.equals(setupAction)) {
			return;
		}

		throw new ApplicationException(
			ErrorCode.INVALID_GITHUB_APP_SETUP_ACTION,
			"Github App 최초 설치 완료 요청은 install 작업만 허용됩니다."
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
				"팀 스페이스 멤버만 Github 연동 상태를 조회할 수 있습니다."
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
				"팀 스페이스 호스트만 Github Organization 연결을 진행할 수 있습니다."
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

	private record ConnectedGithubInstallationIds(
		Long githubInstallationId,
		Long installationId
	) {
	}
}

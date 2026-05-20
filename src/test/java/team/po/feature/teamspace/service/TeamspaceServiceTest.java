package team.po.feature.teamspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class TeamspaceServiceTest {

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;

	@Mock
	private ProjectGroupGithubRepositoryRepository projectGroupGithubRepository;

	@InjectMocks
	private TeamspaceService teamspaceService;

	@Test
	void getGithubInstallationStatus_returnsDisconnected_whenGithubInstallationIsNotConnected() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.empty());

		GetGithubInstallationStatusResponse response = teamspaceService.getGithubInstallationStatus(10L, 1L);

		assertThat(response.connected()).isFalse();
		assertThat(response.organizationLogin()).isNull();
		assertThat(response.repositoryCount()).isZero();
		verify(projectGroupGithubRepository, never()).countByProjectGroup_IdAndDeletedAtIsNull(10L);
	}

	@Test
	void getGithubInstallationStatus_returnsConnectedStatus_whenGithubInstallationIsConnected() {
		ProjectGroupGithubInstallation githubConnection = ProjectGroupGithubInstallation.builder()
			.projectGroup(projectGroup())
			.githubInstallation(githubInstallation())
			.connectedBy(user())
			.build();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.of(githubConnection));
		when(projectGroupGithubRepository.countByProjectGroup_IdAndDeletedAtIsNull(10L)).thenReturn(2L);

		GetGithubInstallationStatusResponse response = teamspaceService.getGithubInstallationStatus(10L, 1L);

		assertThat(response.connected()).isTrue();
		assertThat(response.organizationLogin()).isEqualTo("student-team-org");
		assertThat(response.repositoryCount()).isEqualTo(2L);
	}

	@Test
	void getGithubInstallationStatus_throwsForbidden_whenRequesterIsNotProjectGroupMember() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 99L)).thenReturn(false);

		assertThatThrownBy(() -> teamspaceService.getGithubInstallationStatus(10L, 99L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());

		verify(projectGroupGithubInstallationRepository, never()).findByProjectGroup_IdAndDeletedAtIsNull(10L);
		verify(projectGroupGithubRepository, never()).countByProjectGroup_IdAndDeletedAtIsNull(10L);
	}

	private ProjectGroup projectGroup() {
		return ProjectGroup.builder()
			.projectName("TeamPo")
			.projectTitle("TeamPo")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
	}

	private GithubInstallation githubInstallation() {
		return GithubInstallation.builder()
			.installationId(12345L)
			.accountId(98765L)
			.accountLogin("student-team-org")
			.accountType(GithubInstallation.ORGANIZATION_ACCOUNT_TYPE)
			.build();
	}

	private Users user() {
		return Users.builder()
			.email("tester@example.com")
			.nickname("tester")
			.level(1)
			.temperature(50)
			.build();
	}
}

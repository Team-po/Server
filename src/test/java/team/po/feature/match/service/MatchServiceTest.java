package team.po.feature.match.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.feature.match.domain.MatchingMember;
import team.po.feature.match.domain.MatchingSession;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.MatchMemberResponse;
import team.po.feature.match.dto.MatchProjectResponse;
import team.po.feature.match.enums.Role;
import team.po.feature.match.exception.MatchAccessDeniedException;
import team.po.feature.match.exception.MatchDataIntegrityException;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.MatchingSessionRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

	@Mock
	private MatchingSessionRepository matchingSessionRepository;

	@Mock
	private MatchingMemberRepository matchingMemberRepository;

	@Mock
	private ProjectRequestRepository projectRequestRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private MatchService matchService;

	private Users createUser(Long id) {
		Users user = Users.builder()
			.email("test@email.com")
			.password("password")
			.nickname("tester")
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private MatchingSession createSession(Long id) {
		MatchingSession session = MatchingSession.create();
		ReflectionTestUtils.setField(session, "id", id);
		return session;
	}

	private ProjectRequest createHostRequest(Users user) {
		return ProjectRequest.builder()
			.user(user)
			.role(Role.BACKEND)
			.projectTitle("팀포")
			.projectDescription("설명")
			.projectMvp("MVP")
			.build();
	}

	private ProjectRequest createMemberRequest(Users user) {
		return ProjectRequest.builder()
			.user(user)
			.role(Role.FRONTEND)
			.build();
	}

	// ===== getMatchMembers =====

	@Test
	void getMatchMembers_success() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		MatchingMember member = MatchingMember.createForHost(42L, 1L, 1L);
		ReflectionTestUtils.setField(member, "id", 1L);

		ProjectRequest pr = createHostRequest(loginUser);
		ReflectionTestUtils.setField(pr, "id", 1L);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllByMatchingSessionId(42L)).thenReturn(List.of(member));
		when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(loginUser));
		when(projectRequestRepository.findAllById(List.of(1L))).thenReturn(List.of(pr));

		MatchMemberResponse response = matchService.getMatchMembers(42L, loginUser);

		assertThat(response.matchId()).isEqualTo(42L);
		assertThat(response.members()).hasSize(1);
		assertThat(response.members().get(0).nickname()).isEqualTo("tester");
		assertThat(response.members().get(0).isHost()).isTrue();
	}

	@Test
	void getMatchMembers_throwsNotFound_whenSessionNotExists() {
		Users loginUser = createUser(1L);
		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.getMatchMembers(42L, loginUser))
			.isInstanceOf(MatchAccessDeniedException.class);
	}

	@Test
	void getMatchMembers_throwsForbidden_whenNotMember() {
		Users loginUser = createUser(2L);
		MatchingSession session = createSession(42L);
		MatchingMember member = MatchingMember.createForHost(42L, 1L, 1L);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllByMatchingSessionId(42L)).thenReturn(List.of(member));

		assertThatThrownBy(() -> matchService.getMatchMembers(42L, loginUser))
			.isInstanceOf(MatchAccessDeniedException.class);
	}

	// ===== getMatchProject =====

	@Test
	void getMatchProject_success() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		MatchingMember member = MatchingMember.createForHost(42L, 1L, 1L);

		ProjectRequest pr = createHostRequest(loginUser);
		ReflectionTestUtils.setField(pr, "id", 1L);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllByMatchingSessionId(42L)).thenReturn(List.of(member));
		when(projectRequestRepository.findAllById(List.of(1L))).thenReturn(List.of(pr));

		MatchProjectResponse response = matchService.getMatchProject(42L, loginUser);

		assertThat(response.matchId()).isEqualTo(42L);
		assertThat(response.projectTitle()).isEqualTo("팀포");
	}

	@Test
	void getMatchProject_throwsNotFound_whenSessionNotExists() {
		Users loginUser = createUser(1L);
		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(MatchAccessDeniedException.class);
	}

	@Test
	void getMatchProject_throwsForbidden_whenNotMember() {
		Users loginUser = createUser(2L);
		MatchingSession session = createSession(42L);
		MatchingMember member = MatchingMember.createForHost(42L, 1L, 1L);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllByMatchingSessionId(42L)).thenReturn(List.of(member));

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(MatchAccessDeniedException.class);
	}

	@Test
	void getMatchProject_throwsDataIntegrity_whenNoHost() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		MatchingMember member = MatchingMember.createForMember(42L, 1L, 1L);

		ProjectRequest pr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(pr, "id", 1L);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllByMatchingSessionId(42L)).thenReturn(List.of(member));
		when(projectRequestRepository.findAllById(List.of(1L))).thenReturn(List.of(pr));

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(MatchDataIntegrityException.class);
	}

	@Test
	void getMatchProject_throwsDataIntegrity_whenHostNotAccepted() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		MatchingMember member = MatchingMember.createForMember(42L, 1L, 1L);

		ProjectRequest pr = createHostRequest(loginUser);
		ReflectionTestUtils.setField(pr, "id", 1L);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllByMatchingSessionId(42L)).thenReturn(List.of(member));
		when(projectRequestRepository.findAllById(List.of(1L))).thenReturn(List.of(pr));

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(MatchDataIntegrityException.class);
	}
}
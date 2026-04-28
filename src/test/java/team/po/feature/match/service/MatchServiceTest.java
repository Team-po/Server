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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.exception.ApplicationException;
import team.po.feature.match.domain.MatchingMember;
import team.po.feature.match.domain.MatchingSession;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.MatchMemberResponse;
import team.po.feature.match.dto.MatchProjectResponse;
import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;
import team.po.feature.match.event.MatchAcceptedEvent;
import team.po.feature.match.event.MatchRejectedEvent;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.MatchingSessionRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.match.strategy.MatchConstants;
import team.po.feature.projectgroup.service.ProjectGroupService;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

	@Mock
	private MatchingSessionRepository matchingSessionRepository;
	@Mock
	private MatchingMemberRepository matchingMemberRepository;
	@Mock
	private ProjectRequestRepository projectRequestRepository;
	@Mock
	private ApplicationEventPublisher eventPublisher;
	@Mock
	private ProjectGroupService projectGroupService;

	@InjectMocks
	private MatchService matchService;

	// ===== 픽스처 =====

	private Users createUser(Long id) {
		Users user = Users.builder()
			.email("test" + id + "@email.com")
			.password("password")
			.nickname("tester" + id)
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

	private MatchingMember createHostMember(MatchingSession session, ProjectRequest pr) {
		return MatchingMember.createForHost(session, pr);
	}

	private MatchingMember createMemberMember(MatchingSession session, ProjectRequest pr) {
		return MatchingMember.createForMember(session, pr);
	}

	// ===== getMatchMembers =====

	@Test
	void getMatchMembers_success() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(loginUser);
		ReflectionTestUtils.setField(hostPr, "id", 1L);
		MatchingMember hostMember = createHostMember(session, hostPr);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		MatchMemberResponse response = matchService.getMatchMembers(42L, loginUser);

		assertThat(response.matchId()).isEqualTo(42L);
		assertThat(response.members()).hasSize(1);
		assertThat(response.members().get(0).nickname()).isEqualTo("tester1");
		assertThat(response.members().get(0).isHost()).isTrue();
	}

	@Test
	void getMatchMembers_throwsNotFound_whenSessionNotExists() {
		Users loginUser = createUser(1L);
		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.getMatchMembers(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	@Test
	void getMatchMembers_throwsForbidden_whenNotMember() {
		Users loginUser = createUser(2L);
		Users otherUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(otherUser);
		MatchingMember hostMember = createHostMember(session, hostPr);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		assertThatThrownBy(() -> matchService.getMatchMembers(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	// ===== getMatchProject =====

	@Test
	void getMatchProject_success() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(loginUser);
		ReflectionTestUtils.setField(hostPr, "id", 1L);
		MatchingMember hostMember = createHostMember(session, hostPr);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		MatchProjectResponse response = matchService.getMatchProject(42L, loginUser);

		assertThat(response.matchId()).isEqualTo(42L);
		assertThat(response.projectTitle()).isEqualTo("팀포");
	}

	@Test
	void getMatchProject_throwsNotFound_whenSessionNotExists() {
		Users loginUser = createUser(1L);
		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	@Test
	void getMatchProject_throwsForbidden_whenNotMember() {
		Users loginUser = createUser(2L);
		Users otherUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(otherUser);
		MatchingMember hostMember = createHostMember(session, hostPr);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	@Test
	void getMatchProject_throwsDataIntegrity_whenNoHost() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest memberPr = createMemberRequest(loginUser);
		MatchingMember memberMember = createMemberMember(session, memberPr);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(memberMember));

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	@Test
	void getMatchProject_throwsDataIntegrity_whenHostNotAccepted() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(loginUser);
		MatchingMember hostMember = createHostMember(session, hostPr);
		ReflectionTestUtils.setField(hostMember, "isAccepted", null);

		when(matchingSessionRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		assertThatThrownBy(() -> matchService.getMatchProject(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	// ===== accept =====

	@Test
	void accept_success_notAllAccepted() {
		Users hostUser = createUser(1L);
		Users loginUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest hostPr = createHostRequest(hostUser);
		ReflectionTestUtils.setField(hostPr, "id", 1L);
		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 2L);

		MatchingMember hostMember = createHostMember(session, hostPr);
		MatchingMember myMember = createMemberMember(session, myPr);

		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));
		when(matchingMemberRepository.isAllAccepted(42L, MatchConstants.TEAM_SIZE)).thenReturn(false);

		matchService.accept(42L, loginUser);

		assertThat(myMember.getIsAccepted()).isTrue();
		verify(matchingMemberRepository).isAllAccepted(42L, MatchConstants.TEAM_SIZE);
		verify(eventPublisher).publishEvent(any(MatchAcceptedEvent.class));
	}

	@Test
	void accept_idempotent_whenAlreadyAccepted() {
		Users hostUser = createUser(1L);
		Users loginUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest hostPr = createHostRequest(hostUser);
		ReflectionTestUtils.setField(hostPr, "id", 1L);
		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 2L);

		MatchingMember hostMember = createHostMember(session, hostPr);
		MatchingMember myMember = createMemberMember(session, myPr);
		myMember.accept();

		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));

		matchService.accept(42L, loginUser);

		verify(matchingMemberRepository, never()).isAllAccepted(any(), anyInt());
	}

	@Test
	void accept_throwsForbidden_whenHost() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(loginUser);
		MatchingMember hostMember = createHostMember(session, hostPr);

		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		assertThatThrownBy(() -> matchService.accept(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	// ===== reject =====

	@Test
	void reject_success() {
		Users hostUser = createUser(1L);
		Users loginUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest hostPr = createHostRequest(hostUser);
		ReflectionTestUtils.setField(hostPr, "id", 1L);
		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 2L);
		myPr.startMatching();

		MatchingMember hostMember = createHostMember(session, hostPr);
		MatchingMember myMember = createMemberMember(session, myPr);

		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));

		matchService.reject(42L, loginUser);

		assertThat(myMember.getIsAccepted()).isFalse();
		assertThat(myMember.isDeleted()).isTrue();
		assertThat(myPr.getStatus()).isEqualTo(Status.WAITING);
		verify(eventPublisher).publishEvent(any(MatchRejectedEvent.class));
	}

	@Test
	void reject_throwsForbidden_whenHost() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(loginUser);
		MatchingMember hostMember = createHostMember(session, hostPr);

		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		assertThatThrownBy(() -> matchService.reject(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	@Test
	void reject_throwsBadRequest_whenAlreadyAccepted() {
		Users hostUser = createUser(1L);
		Users loginUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest hostPr = createHostRequest(hostUser);
		ReflectionTestUtils.setField(hostPr, "id", 1L);
		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 2L);

		MatchingMember hostMember = createHostMember(session, hostPr);
		MatchingMember myMember = createMemberMember(session, myPr);
		myMember.accept();

		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));

		assertThatThrownBy(() -> matchService.reject(42L, loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	// ===== cancel =====

	@Test
	void cancel_success_whenWaiting() {
		Users loginUser = createUser(1L);
		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 1L);

		when(projectRequestRepository.findByUserIdAndStatusIn(eq(1L), any()))
			.thenReturn(Optional.of(myPr));

		matchService.cancel(loginUser);

		assertThat(myPr.getStatus()).isEqualTo(Status.CANCELED);
	}

	@Test
	void cancel_success_whenMatchingMember() {
		Users loginUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 2L);
		myPr.startMatching();

		MatchingMember myMember = createMemberMember(session, myPr);
		ReflectionTestUtils.setField(myMember, "id", 2L);

		when(projectRequestRepository.findByUserIdAndStatusIn(eq(2L), any()))
			.thenReturn(Optional.of(myPr));
		when(matchingMemberRepository.findCurrentActiveByUserId(2L))
			.thenReturn(Optional.of(myMember));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(myMember));

		matchService.cancel(loginUser);

		assertThat(myPr.getStatus()).isEqualTo(Status.CANCELED);
		assertThat(myMember.isDeleted()).isTrue();
	}

	@Test
	void cancel_success_whenMatchingHost_disbandSession() {
		Users loginUser = createUser(1L);
		Users memberUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest hostPr = createHostRequest(loginUser);
		ReflectionTestUtils.setField(hostPr, "id", 1L);
		hostPr.startMatching();

		ProjectRequest memberPr = createMemberRequest(memberUser);
		ReflectionTestUtils.setField(memberPr, "id", 2L);
		memberPr.startMatching();

		MatchingMember hostMember = createHostMember(session, hostPr);
		MatchingMember memberMember = createMemberMember(session, memberPr);

		when(projectRequestRepository.findByUserIdAndStatusIn(eq(1L), any()))
			.thenReturn(Optional.of(hostPr));
		when(matchingMemberRepository.findCurrentActiveByUserId(1L))
			.thenReturn(Optional.of(hostMember));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, memberMember));

		matchService.cancel(loginUser);

		assertThat(hostPr.getStatus()).isEqualTo(Status.CANCELED);
		assertThat(memberPr.getStatus()).isEqualTo(Status.WAITING);
		assertThat(hostMember.isDeleted()).isTrue();
		assertThat(memberMember.isDeleted()).isTrue();
		assertThat(session.isDeleted()).isTrue();
	}

	@Test
	void cancel_throwsNotFound_whenNoActiveRequest() {
		Users loginUser = createUser(1L);

		when(projectRequestRepository.findByUserIdAndStatusIn(eq(1L), any()))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.cancel(loginUser))
			.isInstanceOf(ApplicationException.class);
	}
}
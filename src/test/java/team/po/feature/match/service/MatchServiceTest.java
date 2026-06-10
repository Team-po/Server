package team.po.feature.match.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import team.po.feature.match.event.MatchCreatedEvent;
import team.po.feature.match.event.MatchOrphanSessionCleanedEvent;
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

		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.of(hostMember));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		MatchMemberResponse response = matchService.getMatchMembers(loginUser);

		assertThat(response.members()).hasSize(1);
		assertThat(response.members().get(0).nickname()).isEqualTo("tester1");
		assertThat(response.members().get(0).isHost()).isTrue();
	}

	@Test
	void getMatchMembers_throwsNotFound_whenNoActiveSession() {
		Users loginUser = createUser(1L);
		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.getMatchMembers(loginUser))
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

		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.of(hostMember));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		MatchProjectResponse response = matchService.getMatchProject(loginUser);

		assertThat(response.projectTitle()).isEqualTo("팀포");
	}

	@Test
	void getMatchProject_throwsNotFound_whenNoActiveSession() {
		Users loginUser = createUser(1L);
		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchService.getMatchProject(loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	@Test
	void getMatchProject_throwsDataIntegrity_whenNoHost() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest memberPr = createMemberRequest(loginUser);
		MatchingMember memberMember = createMemberMember(session, memberPr);

		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.of(memberMember));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(memberMember));

		assertThatThrownBy(() -> matchService.getMatchProject(loginUser))
			.isInstanceOf(ApplicationException.class);
	}

	@Test
	void getMatchProject_throwsDataIntegrity_whenHostNotAccepted() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(loginUser);
		MatchingMember hostMember = createHostMember(session, hostPr);
		ReflectionTestUtils.setField(hostMember, "isAccepted", null);

		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.of(hostMember));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L)).thenReturn(List.of(hostMember));

		assertThatThrownBy(() -> matchService.getMatchProject(loginUser))
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

		when(matchingMemberRepository.findCurrentActiveByUserId(2L)).thenReturn(Optional.of(myMember));
		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));
		when(matchingMemberRepository.isAllAccepted(42L, MatchConstants.TEAM_SIZE)).thenReturn(false);

		matchService.accept(loginUser);

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

		when(matchingMemberRepository.findCurrentActiveByUserId(2L)).thenReturn(Optional.of(myMember));
		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));

		matchService.accept(loginUser);

		verify(matchingMemberRepository, never()).isAllAccepted(any(), anyInt());
	}

	@Test
	void accept_throwsForbidden_whenHost() {
		Users loginUser = createUser(1L);
		MatchingSession session = createSession(42L);
		ProjectRequest hostPr = createHostRequest(loginUser);
		MatchingMember hostMember = createHostMember(session, hostPr);

		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.of(hostMember));
		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));

		assertThatThrownBy(() -> matchService.accept(loginUser))
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

		when(matchingMemberRepository.findCurrentActiveByUserId(2L)).thenReturn(Optional.of(myMember));
		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));

		matchService.reject(loginUser);

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

		when(matchingMemberRepository.findCurrentActiveByUserId(1L)).thenReturn(Optional.of(hostMember));
		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));

		assertThatThrownBy(() -> matchService.reject(loginUser))
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

		when(matchingMemberRepository.findCurrentActiveByUserId(2L)).thenReturn(Optional.of(myMember));
		when(matchingSessionRepository.findByIdWithLock(42L)).thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, myMember));

		assertThatThrownBy(() -> matchService.reject(loginUser))
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
		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
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
		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
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

	// ===== cancelActiveMatchForWithdrawal =====

	@Test
	void cancelActiveMatchForWithdrawal_success_whenWaiting() {
		Users loginUser = createUser(1L);
		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 1L);

		when(projectRequestRepository.findByUserIdAndStatusIn(eq(1L), any()))
			.thenReturn(Optional.of(myPr));

		matchService.cancelActiveMatchForWithdrawal(1L);

		assertThat(myPr.getStatus()).isEqualTo(Status.CANCELED);
		verify(matchingMemberRepository, never()).findCurrentActiveByUserId(any());
	}

	@Test
	void cancelActiveMatchForWithdrawal_success_whenMatchingMember() {
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
		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(myMember));

		matchService.cancelActiveMatchForWithdrawal(2L);

		assertThat(myPr.getStatus()).isEqualTo(Status.CANCELED);
		assertThat(myMember.isDeleted()).isTrue();
	}

	@Test
	void cancelActiveMatchForWithdrawal_success_whenMatchingHost_disbandSession() {
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
		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(hostMember, memberMember));

		matchService.cancelActiveMatchForWithdrawal(1L);

		assertThat(hostPr.getStatus()).isEqualTo(Status.CANCELED);
		assertThat(memberPr.getStatus()).isEqualTo(Status.WAITING);
		assertThat(hostMember.isDeleted()).isTrue();
		assertThat(memberMember.isDeleted()).isTrue();
		assertThat(session.isDeleted()).isTrue();
	}

	@Test
	void cancelActiveMatchForWithdrawal_returnsWhenNoActiveRequest() {
		when(projectRequestRepository.findByUserIdAndStatusIn(eq(1L), any()))
			.thenReturn(Optional.empty());

		matchService.cancelActiveMatchForWithdrawal(1L);

		verify(matchingMemberRepository, never()).findCurrentActiveByUserId(any());
		verify(matchingSessionRepository, never()).findByIdWithLock(any());
	}

	@Test
	void cancelActiveMatchForWithdrawal_returnsWhenMatchingMemberAlreadyRemoved() {
		Users loginUser = createUser(1L);
		ProjectRequest myPr = createMemberRequest(loginUser);
		ReflectionTestUtils.setField(myPr, "id", 1L);
		myPr.startMatching();

		when(projectRequestRepository.findByUserIdAndStatusIn(eq(1L), any()))
			.thenReturn(Optional.of(myPr));
		when(matchingMemberRepository.findCurrentActiveByUserId(1L))
			.thenReturn(Optional.empty());

		matchService.cancelActiveMatchForWithdrawal(1L);

		assertThat(myPr.getStatus()).isEqualTo(Status.MATCHING);
		verify(matchingSessionRepository, never()).findByIdWithLock(any());
	}

	// ===== abandonOrphanSession =====

	@Test
	void abandonOrphanSession_success_revertsAllMembersAndDisbandsSession() {
		// Given: 호스트가 누락된 세션 (멤버 2명만 활성 상태)
		Users memberUser1 = createUser(2L);
		Users memberUser2 = createUser(3L);
		MatchingSession session = createSession(42L);

		ProjectRequest memberPr1 = createMemberRequest(memberUser1);
		ReflectionTestUtils.setField(memberPr1, "id", 1L);
		memberPr1.startMatching();

		ProjectRequest memberPr2 = createMemberRequest(memberUser2);
		ReflectionTestUtils.setField(memberPr2, "id", 2L);
		memberPr2.startMatching();

		MatchingMember member1 = createMemberMember(session, memberPr1);
		MatchingMember member2 = createMemberMember(session, memberPr2);

		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of(member1, member2));

		// When
		matchService.abandonOrphanSession(42L);

		// Then
		assertThat(memberPr1.getStatus()).isEqualTo(Status.WAITING);
		assertThat(memberPr2.getStatus()).isEqualTo(Status.WAITING);
		assertThat(member1.isDeleted()).isTrue();
		assertThat(member2.isDeleted()).isTrue();
		assertThat(session.isDeleted()).isTrue();

		ArgumentCaptor<MatchOrphanSessionCleanedEvent> captor =
			ArgumentCaptor.forClass(MatchOrphanSessionCleanedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());

		MatchOrphanSessionCleanedEvent event = captor.getValue();
		assertThat(event.matchSessionId()).isEqualTo(42L);
		assertThat(event.restoreMemberUserIds()).containsExactlyInAnyOrder(2L, 3L);
	}

	@Test
	void abandonOrphanSession_success_whenNoMembers() {
		// Given
		MatchingSession session = createSession(42L);

		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
		when(matchingMemberRepository.findAllActiveBySessionIdWithFetch(42L))
			.thenReturn(List.of());

		// When
		matchService.abandonOrphanSession(42L);

		// Then
		assertThat(session.isDeleted()).isTrue();

		ArgumentCaptor<MatchOrphanSessionCleanedEvent> captor =
			ArgumentCaptor.forClass(MatchOrphanSessionCleanedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().restoreMemberUserIds()).isEmpty();
	}

	// ===== fillVacancy =====

	@Test
	void fillVacancy_success_addsNewMemberToSession() {
		// Given: WAITING 상태의 후보가 빈자리에 충원됨
		Users candidateUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest candidatePr = createMemberRequest(candidateUser);
		ReflectionTestUtils.setField(candidatePr, "id", 2L);

		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
		when(projectRequestRepository.findById(2L))  // ← 추가
			.thenReturn(Optional.of(candidatePr));

		// When
		matchService.fillVacancy(42L, Role.FRONTEND, candidatePr.getId());

		// Then
		assertThat(candidatePr.getStatus()).isEqualTo(Status.MATCHING);
		verify(matchingMemberRepository).save(any(MatchingMember.class));

		ArgumentCaptor<MatchCreatedEvent> captor = ArgumentCaptor.forClass(MatchCreatedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().matchingSessionId()).isEqualTo(42L);
		assertThat(captor.getValue().memberUserIds()).containsExactly(2L);
	}

	@Test
	void fillVacancy_skip_whenCandidateNotWaiting() {
		Users candidateUser = createUser(2L);
		MatchingSession session = createSession(42L);

		ProjectRequest candidatePr = createMemberRequest(candidateUser);
		ReflectionTestUtils.setField(candidatePr, "id", 2L);
		candidatePr.startMatching();
		candidatePr.cancel();

		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.of(session));
		when(projectRequestRepository.findById(2L))  // ← 추가
			.thenReturn(Optional.of(candidatePr));

		// When
		matchService.fillVacancy(42L, Role.FRONTEND, candidatePr.getId());

		// Then
		verify(matchingMemberRepository, never()).save(any(MatchingMember.class));
		verify(eventPublisher, never()).publishEvent(any());
		assertThat(candidatePr.getStatus()).isEqualTo(Status.CANCELED);
	}

	@Test
	void fillVacancy_throwsNotFound_whenSessionDeleted() {
		// Given: 락 잡으려 했으나 세션이 이미 해산됨 (예: 호스트가 cancel)
		Users candidateUser = createUser(2L);
		ProjectRequest candidatePr = createMemberRequest(candidateUser);
		ReflectionTestUtils.setField(candidatePr, "id", 2L);

		when(matchingSessionRepository.findByIdWithLock(42L))
			.thenReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> matchService.fillVacancy(42L, Role.FRONTEND, candidatePr.getId()))
			.isInstanceOf(ApplicationException.class);

		verify(matchingMemberRepository, never()).save(any(MatchingMember.class));
		verify(eventPublisher, never()).publishEvent(any());
	}
}

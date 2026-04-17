package team.po.feature.match.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;
import team.po.feature.match.exception.ProjectRequestAlreadyExistsException;
import team.po.feature.match.exception.ProjectRequestNotFoundException;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.exception.UserNotFoundException;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProjectRequestServiceTest {

	@Mock
	private ProjectRequestRepository projectRequestRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private ProjectRequestService projectRequestService;

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

	// ========== createProjectRequest ==========

	@Test
	void createProjectRequest_success() {
		Users user = createUser(1L);
		ProjectRequestDto dto = new ProjectRequestDto(Role.BACKEND, "title", "desc", "mvp");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(projectRequestRepository.existsByUserIdAndStatusIn(1L,
			List.of(Status.WAITING, Status.MATCHING))).thenReturn(false);
		projectRequestService.createProjectRequest(user, dto);
		verify(projectRequestRepository).save(any(ProjectRequest.class));
	}

	@Test
	void createProjectRequest_throwsWhenUserNotFound() {
		Users user = createUser(1L);
		ProjectRequestDto dto = new ProjectRequestDto(Role.BACKEND, "title", "desc", "mvp");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> projectRequestService.createProjectRequest(user, dto))
			.isInstanceOf(UserNotFoundException.class);
		verify(projectRequestRepository, never()).save(any());
	}

	@Test
	void createProjectRequest_throwsWhenDuplicateRequest() {
		Users user = createUser(1L);
		ProjectRequestDto dto = new ProjectRequestDto(Role.BACKEND, "title", "desc", "mvp");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(projectRequestRepository.existsByUserIdAndStatusIn(1L,
			List.of(Status.WAITING, Status.MATCHING))).thenReturn(true);
		assertThatThrownBy(() -> projectRequestService.createProjectRequest(user, dto))
			.isInstanceOf(ProjectRequestAlreadyExistsException.class);
		verify(projectRequestRepository, never()).save(any());
	}

	@Test
	void createProjectRequest_throwsWhenRaceCondition() {
		Users user = createUser(1L);
		ProjectRequestDto dto = new ProjectRequestDto(Role.BACKEND, "title", "desc", "mvp");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(projectRequestRepository.existsByUserIdAndStatusIn(any(), any())).thenReturn(false);
		when(projectRequestRepository.save(any())).thenThrow(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> projectRequestService.createProjectRequest(user, dto))
			.isInstanceOf(ProjectRequestAlreadyExistsException.class);
	}

	// ========== cancelProjectRequest ==========

	@Test
	void cancelProjectRequest_success() {
		Users user = createUser(1L);
		ProjectRequest request = ProjectRequest.builder().user(user).role(Role.BACKEND).build();
		when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(
			Optional.of(request));
		projectRequestService.cancelProjectRequest(user);
		assertThat(request.getStatus()).isEqualTo(Status.CANCELED);
	}

	@Test
	void cancelProjectRequest_throwsWhenNoActiveRequest() {
		Users user = createUser(1L);
		when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(
			Optional.empty());
		assertThatThrownBy(() -> projectRequestService.cancelProjectRequest(user))
			.isInstanceOf(ProjectRequestNotFoundException.class);
	}

	@Test
	void cancelProjectRequest_throwsWhenMatched() {
		Users user = createUser(1L);
		// MATCHED는 findBy 대상이 아니니까 empty 반환
		when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(
			Optional.empty());
		assertThatThrownBy(() -> projectRequestService.cancelProjectRequest(user))
			.isInstanceOf(ProjectRequestNotFoundException.class);
	}

	// ========== getProjectRequestStatus ==========

	@Test
	void getProjectRequestStatus_success_whenWaiting() {
		Users user = createUser(1L);
		ProjectRequest request = ProjectRequest.builder().user(user).role(Role.BACKEND).build();
		when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(
			Optional.of(request));
		ProjectRequestStatusResponse response = projectRequestService.getProjectRequestStatus(user);
		assertThat(response.status()).isEqualTo(Status.WAITING);
		assertThat(response.matchId()).isNull();
	}

	// whenMatching(): startMatching 구현 후 추가

	@Test
	void getProjectRequestStatus_throwsWhenNoActiveRequest() {
		Users user = createUser(1L);
		when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(
			Optional.empty());
		assertThatThrownBy(() -> projectRequestService.getProjectRequestStatus(user))
			.isInstanceOf(ProjectRequestNotFoundException.class);
	}

	// throwsWhenMatchingButNoMember: startMatching 구현 후 추가
}
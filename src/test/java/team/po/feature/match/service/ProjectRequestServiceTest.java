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

import team.po.exception.ApplicationException;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProjectRequestServiceTest {

	@Mock
	private ProjectRequestRepository projectRequestRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private MatchingMemberRepository matchingMemberRepository;

	@InjectMocks
	private ProjectRequestService projectRequestService;

	private Users createUser(Long id) {
		Users user = Users.builder().email("test@email.com").build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	// ========== createProjectRequest ==========

	@Test
	void createProjectRequest_success_asHost() {
		Users user = createUser(1L);
		ProjectRequestDto dto = new ProjectRequestDto(Role.BACKEND, "title", "desc", "mvp");

		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(projectRequestRepository.existsByUserIdAndStatusIn(anyLong(), anyList())).thenReturn(false);

		projectRequestService.createProjectRequest(user, dto);

		verify(projectRequestRepository).save(argThat(pr -> pr.isHostRequest() == true));
	}

	@Test
	void createProjectRequest_success_asMember() {
		Users user = createUser(1L);
		ProjectRequestDto dto = new ProjectRequestDto(Role.BACKEND, null, null, null);

		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(projectRequestRepository.existsByUserIdAndStatusIn(anyLong(), anyList())).thenReturn(false);

		projectRequestService.createProjectRequest(user, dto);

		verify(projectRequestRepository).save(argThat(pr -> pr.isHostRequest() == false));
	}

	@Test
	void createProjectRequest_throwsWhenDuplicate() {
		Users user = createUser(1L);
		ProjectRequestDto dto = new ProjectRequestDto(Role.BACKEND, "title", "desc", "mvp");

		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(projectRequestRepository.existsByUserIdAndStatusIn(1L,
			List.of(Status.WAITING, Status.MATCHING))).thenReturn(true);

		assertThatThrownBy(() -> projectRequestService.createProjectRequest(user, dto))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("이미 진행 중인 매칭 요청이 있습니다.");
	}
}
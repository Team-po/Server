package team.po.feature.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import org.springframework.test.util.ReflectionTestUtils;
import team.po.common.auth.LoginUserInfo;
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

    @Test
    void createProjectRequest_success() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        ProjectRequestDto dto = new ProjectRequestDto(Role.BE, "title", "desc", "mvp");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(projectRequestRepository.existsByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING, Status.MATCHED))).thenReturn(false);

        projectRequestService.createProjectRequest(loginUser, dto);

        verify(projectRequestRepository).save(any(ProjectRequest.class));
    }

    @Test
    void createProjectRequest_throwsWhenUserNotFound() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        ProjectRequestDto dto = new ProjectRequestDto(Role.BE, "title", "desc", "mvp");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectRequestService.createProjectRequest(loginUser, dto))
                .isInstanceOf(UserNotFoundException.class);

        verify(projectRequestRepository, never()).save(any());
    }

    @Test
    void createProjectRequest_throwsWhenDuplicateRequest() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        ProjectRequestDto dto = new ProjectRequestDto(Role.BE, "title", "desc", "mvp");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(projectRequestRepository.existsByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING, Status.MATCHED))).thenReturn(true);

        assertThatThrownBy(() -> projectRequestService.createProjectRequest(loginUser, dto))
                .isInstanceOf(ProjectRequestAlreadyExistsException.class);

        verify(projectRequestRepository, never()).save(any());
    }

    @Test
    void cancelProjectRequest_success() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ProjectRequest request = ProjectRequest.builder().user(user).role(Role.BE).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(Optional.of(request));

        projectRequestService.cancelProjectRequest(loginUser);

        assertThat(request.getStatus()).isEqualTo(Status.CANCELED);
    }

    @Test
    void cancelProjectRequest_throwsWhenUserNotFound() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectRequestService.cancelProjectRequest(loginUser))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void cancelProjectRequest_throwsWhenNoActiveRequest() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectRequestService.cancelProjectRequest(loginUser))
                .isInstanceOf(ProjectRequestNotFoundException.class);
    }

    @Test
    void getProjectRequestStatus_success() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ProjectRequest request = ProjectRequest.builder().user(user).role(Role.BE).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(Optional.of(request));

        ProjectRequestStatusResponse response = projectRequestService.getProjectRequestStatus(loginUser);

        assertThat(response.status()).isEqualTo(Status.WAITING);
    }

    @Test
    void getProjectRequestStatus_throwsWhenUserNotFound() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectRequestService.getProjectRequestStatus(loginUser))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getProjectRequestStatus_throwsWhenNoActiveRequest() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectRequestService.getProjectRequestStatus(loginUser))
                .isInstanceOf(ProjectRequestNotFoundException.class);
    }

    @Test
    void createProjectRequest_throwsWhenRaceCondition() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        ProjectRequestDto dto = new ProjectRequestDto(Role.BE, "title", "desc", "mvp");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(projectRequestRepository.existsByUserIdAndStatusIn(any(), any())).thenReturn(false);
        when(projectRequestRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> projectRequestService.createProjectRequest(loginUser, dto))
                .isInstanceOf(ProjectRequestAlreadyExistsException.class);
    }

    @Test
    void cancelProjectRequest_throwsWhenMatched() {
        LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
        Users user = Users.builder().email("test@email.com").password("password").nickname("tester").level(1).temperature(50).build();
        ReflectionTestUtils.setField(user, "id", loginUser.id());

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        // MATCHED는 findBy 대상이 아니니까 empty 반환
        when(projectRequestRepository.findByUserIdAndStatusIn(1L, List.of(Status.WAITING, Status.MATCHING))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectRequestService.cancelProjectRequest(loginUser))
                .isInstanceOf(ProjectRequestNotFoundException.class);
    }
}
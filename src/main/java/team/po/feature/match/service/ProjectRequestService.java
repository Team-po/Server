package team.po.feature.match.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.po.common.auth.LoginUserInfo;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.enums.Status;
import team.po.feature.match.exception.ProjectRequestAlreadyExistsException;
import team.po.feature.match.exception.ProjectRequestNotFoundException;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.exception.UserNotFoundException;
import team.po.feature.user.repository.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectRequestService {
    private final ProjectRequestRepository projectRequestRepository;
    private final UserRepository userRepository;


    @Transactional
    public void createProjectRequest(LoginUserInfo loginUser, ProjectRequestDto dto) {
        Users user = this.getActiveUser(loginUser.id());

        boolean matchingExists = projectRequestRepository.existsByUserIdAndStatusIn(
                loginUser.id(),
                List.of(Status.WAITING, Status.MATCHING, Status.MATCHED)
                // TODO: MATCHED인 경우 ProjectGroup.status 확인 필요
                // 프로젝트가 종료된 경우에만 새 매칭 요청 가능
        );
        if (matchingExists) {
            throw new ProjectRequestAlreadyExistsException(
                    HttpStatus.CONFLICT,
                    ErrorCodeConstants.PROJECT_REQUEST_ALREADY_EXISTS,
                    "이미 진행 중인 매칭 요청이 있습니다."
            );
        }

        try {
            ProjectRequest request = ProjectRequest.builder()
                    .user(user)
                    .role(dto.role())
                    .projectTitle(dto.projectTitle())
                    .projectDescription(dto.projectDescription())
                    .projectMvp(dto.projectMvp())
                    .build();
            projectRequestRepository.save(request);
        } catch (DataIntegrityViolationException e) { // 동시 요청 Race Condition
            throw new ProjectRequestAlreadyExistsException(
                    HttpStatus.CONFLICT,
                    ErrorCodeConstants.PROJECT_REQUEST_ALREADY_EXISTS,
                    "이미 진행 중인 매칭 요청이 있습니다."
            );
        }
    }

    @Transactional
    public void cancelProjectRequest(LoginUserInfo loginUser){
        // validate active user
        this.getActiveUser(loginUser.id());

        ProjectRequest request = projectRequestRepository.findByUserIdAndStatusIn(
                loginUser.id(), // Controller
                List.of(Status.WAITING, Status.MATCHING)
        ).orElseThrow(() -> new ProjectRequestNotFoundException(
                HttpStatus.NOT_FOUND,
                ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
                "취소할 수 있는 매칭 요청이 없습니다."
        ));
        request.cancel();
    }

    @Transactional(readOnly = true)
    public ProjectRequestStatusResponse getProjectRequestStatus(LoginUserInfo loginUser) {
        this.getActiveUser(loginUser.id());

        ProjectRequest request = projectRequestRepository.findByUserIdAndStatusIn(
                loginUser.id(),
                List.of(Status.WAITING, Status.MATCHING)
        ).orElseThrow(() -> new ProjectRequestNotFoundException(
                HttpStatus.NOT_FOUND,
                ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
                "진행 중인 매칭 요청이 없습니다."
        ));

        return new ProjectRequestStatusResponse(request.getStatus());
    }

    private Users getActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCodeConstants.UNEXISTED_USER,
                        "존재하지 않는 유저입니다."
                ));
    }
}

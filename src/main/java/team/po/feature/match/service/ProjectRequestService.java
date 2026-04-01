package team.po.feature.match.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import team.po.common.auth.LoginUserInfo;
import team.po.common.util.SecurityUtil;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.enums.Status;
import team.po.feature.match.exception.ProjectRequestNotFoundException;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectRequestService {
    private final ProjectRequestRepository projectRequestRepository;
    private final UserRepository userRepository;


    public void createProjectRequest(ProjectRequestDto dto) {
        // Controller에서 User 직접 주입
        // 이메일 전달 X 토큰으로 처리

        Users user;

        ProjectRequest request = ProjectRequest.builder()
                .user(user)
                .role(dto.role())
                .projectTitle(dto.projectTitle())
                .projectDescription(dto.projectDescription())
                .projectMvp(dto.projectMvp())
                .build();

        projectRequestRepository.save(request);

        // 저장하기 전에 따닥 중복 막아야 됨
        // 중복 검사나 엣지케이스 처리
        //
    }

    public void cancelProjectRequest(LoginUserInfo loginUser){
        // UserId + (Status == WAITING || Status == MATCHING)인 요청 찾기
        ProjectRequest request = projectRequestRepository.findByUserIdAndStatusIn(
                loginUser.id(), // login user 정보는 controller에서 주입
                List.of(Status.WAITING, Status.MATCHING)
        ).orElseThrow(() -> new ProjectRequestNotFoundException(
                HttpStatus.NOT_FOUND,
                ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
                "취소할 수 있는 매칭 요청이 없습니다."
        ));
        // 그 요청.cancel
        request.cancel();
    }
}

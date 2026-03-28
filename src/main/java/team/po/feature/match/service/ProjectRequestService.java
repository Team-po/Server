package team.po.feature.match.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.po.common.util.SecurityUtil;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

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
}

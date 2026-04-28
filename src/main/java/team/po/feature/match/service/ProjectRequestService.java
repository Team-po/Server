package team.po.feature.match.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.enums.Status;
import team.po.feature.match.exception.MatchDataIntegrityException;
import team.po.feature.match.exception.ProjectRequestAlreadyExistsException;
import team.po.feature.match.exception.ProjectRequestNotFoundException;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.exception.UserNotFoundException;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectRequestService {
	private final ProjectRequestRepository projectRequestRepository;
	private final UserRepository userRepository;
	private final MatchingMemberRepository matchingMemberRepository;

	@Transactional
	public void createProjectRequest(Users loginUser, ProjectRequestDto request) {
		Users user = userRepository.findByIdAndDeletedAtIsNull(loginUser.getId())
			.orElseThrow(() -> new UserNotFoundException(
				HttpStatus.UNAUTHORIZED,
				ErrorCodeConstants.UNEXISTED_USER,
				"존재하지 않는 유저입니다."
			));
		boolean matchingExists = projectRequestRepository.existsByUserIdAndStatusIn(
			user.getId(),
			List.of(Status.WAITING, Status.MATCHING)
		);
		if (matchingExists) {
			throw new ProjectRequestAlreadyExistsException(
				HttpStatus.CONFLICT,
				ErrorCodeConstants.PROJECT_REQUEST_ALREADY_EXISTS,
				"이미 진행 중인 매칭 요청이 있습니다."
			);
		}

		// TODO: ProjectGroup 구현 후 추가
		// ProjectGroup.status - 프로젝트 진행 중 -> X
		//        boolean projectInProgress = projectGroupRepository.existsByUserIdAndStatus(
		//                user.getId(),
		//                ProjectGroupStatus.IN_PROGRESS);
		//        if (projectInProgress) {
		//            throw new ProjectAlreadyInProgressException(
		//                    HttpStatus.CONFLICT,
		//                    ErrorCodeConstants.PROJECT_ALREADY_IN_PROGRESS,
		//                    "이미 진행 중인 프로젝트가 있습니다."
		//            )
		//        }

		try {
			ProjectRequest projectRequest = ProjectRequest.builder()
				.user(user)
				.role(request.role())
				.projectTitle(request.projectTitle())
				.projectDescription(request.projectDescription())
				.projectMvp(request.projectMvp())
				.build();
			projectRequestRepository.save(projectRequest);
		} catch (DataIntegrityViolationException e) { // 동시 요청 Race Condition
			throw new ProjectRequestAlreadyExistsException(
				HttpStatus.CONFLICT,
				ErrorCodeConstants.PROJECT_REQUEST_ALREADY_EXISTS,
				"이미 진행 중인 매칭 요청이 있습니다."
			);
		}
	}

	@Transactional(readOnly = true)
	public ProjectRequestStatusResponse getProjectRequestStatus(Users user) {
		ProjectRequest projectRequest = projectRequestRepository.findByUserIdAndStatusIn(
			user.getId(),
			List.of(Status.WAITING, Status.MATCHING)
		).orElseThrow(() -> new ProjectRequestNotFoundException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
			"진행 중인 매칭 요청이 없습니다."
		));

		Long matchId = null;
		// MATCHING: matchId 반환
		if (projectRequest.getStatus() == Status.MATCHING) {
			matchId = matchingMemberRepository
				.findCurrentActiveByUserId(user.getId())
				.map(m -> m.getMatchingSession().getId())
				.orElseThrow(() -> {
					log.error("활성 매칭 멤버 데이터 부정합: userId={}", user.getId());
					return new MatchDataIntegrityException(
						HttpStatus.INTERNAL_SERVER_ERROR,
						ErrorCodeConstants.MATCH_DATA_ERROR,
						"MATCHING 상태의 활성 멤버를 찾을 수 없습니다.");
				});
		}
		return new ProjectRequestStatusResponse(projectRequest.getStatus(), matchId);
	}
}

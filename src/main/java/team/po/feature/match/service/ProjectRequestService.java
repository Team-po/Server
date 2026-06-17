package team.po.feature.match.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.enums.Status;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectRequestService {
	private final ProjectRequestRepository projectRequestRepository;
	private final UserRepository userRepository;
	private final MatchingMemberRepository matchingMemberRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;

	@Transactional
	public void createProjectRequest(Users loginUser, ProjectRequestDto request) {
		Users user = userRepository.findByIdAndDeletedAtIsNullForUpdate(loginUser.getId())
			.orElseThrow(() -> new ApplicationException(ErrorCode.UNEXISTED_USER, "존재하지 않는 유저입니다."));
		boolean matchingExists = projectRequestRepository.existsByUserIdAndStatusIn(
			user.getId(),
			List.of(Status.WAITING, Status.MATCHING)
		);
		if (matchingExists) {
			throw new ApplicationException(ErrorCode.PROJECT_REQUEST_ALREADY_EXISTS);
		}

		// 진행 중인 프로젝트가 있으면 매칭 요청 불가
		boolean projectInProgress = projectGroupMemberRepository.existsByUser_IdAndProjectGroup_Status(
			user.getId(),
			ProjectGroupStatus.ACTIVE
		);
		if (projectInProgress) {
			throw new ApplicationException(ErrorCode.PROJECT_ALREADY_IN_PROGRESS);
		}

		try {
			ProjectRequest projectRequest = ProjectRequest.builder()
				.user(user)
				.role(request.role())
				.projectTitle(request.projectTitle())
				.projectDescription(request.projectDescription())
				.projectMvp(request.projectMvp())
				.build();
			projectRequestRepository.save(projectRequest);
			log.info("매칭 요청 생성 완료. projectRequestId: {}, role: {}",
				projectRequest.getId(), projectRequest.getRole());
		} catch (DataIntegrityViolationException e) { // 동시 요청 Race Condition
			throw new ApplicationException(ErrorCode.PROJECT_REQUEST_ALREADY_EXISTS);
		}
	}

	@Transactional
	public void cancelProjectRequest(Users user) {
		ProjectRequest projectRequest = projectRequestRepository.findByUserIdAndStatusIn(
			user.getId(),
			List.of(Status.WAITING, Status.MATCHING)
		).orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_REQUEST_NOT_FOUND, "취소할 수 있는 매칭 요청이 없습니다."));
		projectRequest.cancel();
	}

	@Transactional(readOnly = true)
	public ProjectRequestStatusResponse getProjectRequestStatus(Users user) {
		ProjectRequest projectRequest = projectRequestRepository.findByUserIdAndStatusIn(
			user.getId(),
			List.of(Status.WAITING, Status.MATCHING)
		).orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_REQUEST_NOT_FOUND, "진행 중인 매칭 요청이 없습니다."));

		// MATCHING 상태인데 활성 MatchingMember가 없으면 데이터 불일치
		if (projectRequest.getStatus() == Status.MATCHING) {
			boolean hasActiveMember = matchingMemberRepository
				.findCurrentActiveByUserId(user.getId())
				.isPresent();
			if (!hasActiveMember) {
				log.error("MATCHING 상태지만 활성 매칭 멤버 없음: userId={}", user.getId());
				throw new ApplicationException(ErrorCode.MATCH_DATA_ERROR);
			}
		}

		return new ProjectRequestStatusResponse(
			projectRequest.getStatus(),
			projectRequest.getRole()
		);
	}
}

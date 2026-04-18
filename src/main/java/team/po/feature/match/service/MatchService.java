package team.po.feature.match.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.match.domain.MatchingMember;
import team.po.feature.match.domain.MatchingSession;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.MatchMemberResponse;
import team.po.feature.match.dto.MatchProjectResponse;
import team.po.feature.match.exception.MatchAccessDeniedException;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.MatchingSessionRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
	private final MatchingSessionRepository matchingSessionRepository;
	private final MatchingMemberRepository matchingMemberRepository;
	private final ProjectRequestRepository projectRequestRepository;
	private final UserRepository userRepository;

	// 매칭 세션 멤버 목록 조회
	@Transactional(readOnly = true)
	public MatchMemberResponse getMatchMembers(Long matchId, Users loginUser) {
		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(matchId, loginUser.getId());

		// 2. 멤버 데이터 일괄 조회
		List<Long> userIds = members.stream().map(MatchingMember::getUserId).distinct().toList();
		List<Long> prIds = members.stream().map(MatchingMember::getProjectRequestId).distinct().toList();

		Map<Long, Users> userMap = userRepository.findAllById(userIds).stream()
			.filter(u -> u.getDeletedAt() == null)
			.collect(Collectors.toMap(Users::getId, user -> user));
		Map<Long, ProjectRequest> prMap = projectRequestRepository.findAllById(prIds).stream()
			.collect(Collectors.toMap(ProjectRequest::getId, pr -> pr));

		// 3. MatchingMember dto 매핑
		List<MatchMemberResponse.MemberDto> memberDtos = members.stream()
			.map(member -> {
				Users user = userMap.get(member.getUserId());
				ProjectRequest pr = prMap.get(member.getProjectRequestId());

				if (user == null || pr == null)
					return null;

				return new MatchMemberResponse.MemberDto(
					user.getId(),
					user.getNickname(),
					pr.getRole(),
					user.getLevel(),
					user.getTemperature(),
					user.getProfileImage(),
					pr.isHostRequest(),
					member.getIsAccepted()
				);
			})
			.filter(Objects::nonNull)
			.toList();

		return new MatchMemberResponse(matchId, memberDtos);
	}

	// 매칭 세션 프로젝트 정보 조회
	@Transactional(readOnly = true)
	public MatchProjectResponse getMatchProject(Long matchId, Users loginUser) {
		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(matchId, loginUser.getId());

		// 2. Host 검증 (단일 & 수락 상태)
		List<Long> prIds = members.stream().map(MatchingMember::getProjectRequestId).toList();
		Map<Long, ProjectRequest> prMap = projectRequestRepository.findAllById(prIds).stream()
			.collect(Collectors.toMap(ProjectRequest::getId, pr -> pr));

		List<MatchingMember> hosts = members.stream()
			.filter(m -> prMap.get(m.getProjectRequestId()).isHostRequest())
			.toList();

		if (hosts.size() != 1) {
			log.error("매칭 호스트 데이터 부정합: matchId={}, hostCount={}", matchId, hosts.size());
			throw new IllegalStateException("해당 매칭 세션에 호스트 정보가 없거나 중복되었습니다.");
		}

		MatchingMember hostMember = hosts.getFirst();
		if (!Boolean.TRUE.equals(hostMember.getIsAccepted())) {
			log.error("호스트 수락 상태 부정합: matchId={}, userId={}", matchId, hostMember.getUserId());
			throw new IllegalStateException("호스트의 매칭 수락 상태가 유효하지 않습니다.");
		}

		// 3. Host 프로젝트 정보 조회
		ProjectRequest hostPr = prMap.get(hostMember.getProjectRequestId());

		// 4. 매칭 세션 프로젝트 정보 반환
		return new MatchProjectResponse(
			matchId,
			hostPr.getProjectTitle(),
			hostPr.getProjectDescription(),
			hostPr.getProjectMvp()
		);
	}

	private List<MatchingMember> validateMatchAccessAndGetMembers(Long matchId, Long userId) {
		// 1. 매칭 세션 존재 여부
		MatchingSession session = matchingSessionRepository.findByIdAndDeletedAtIsNull(matchId)
			.orElseThrow(() -> new MatchAccessDeniedException(
				HttpStatus.NOT_FOUND,
				ErrorCodeConstants.MATCH_NOT_FOUND,
				"존재하지 않는 매칭 세션입니다."
			));
		// 2. 해당 세션 매칭 멤버 전체 조회
		List<MatchingMember> members = matchingMemberRepository
			.findAllByMatchingSessionId(matchId);

		// 3. 세션 접근 권한 확인
		boolean isMember = members.stream()
			.anyMatch(m -> m.getUserId().equals(userId));
		// 멤버가 아니라면 접근 불가
		if (!isMember) {
			throw new MatchAccessDeniedException(
				HttpStatus.FORBIDDEN,
				ErrorCodeConstants.MATCH_ACCESS_DENIED,
				"해당 매칭 세션에 접근 권한이 없습니다."
			);
		}

		return members;
	}

}

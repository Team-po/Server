package team.po.feature.devguide.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.client.GeminiClient;
import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGeneration;
import team.po.feature.devguide.domain.DevGuideGenerationType;
import team.po.feature.devguide.domain.DevGuideStatus;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.dto.DevGuideHistoryContentResponse;
import team.po.feature.devguide.dto.DevGuideHistoryListResponse;
import team.po.feature.devguide.dto.DevGuideHistoryResponse;
import team.po.feature.devguide.dto.DevGuideQueryResponse;
import team.po.feature.devguide.dto.DevGuideRegenerateResponse;
import team.po.feature.devguide.prompt.DevGuidePromptBuilder;
import team.po.feature.devguide.prompt.DevGuideSchema;
import team.po.feature.devguide.repository.DevGuideGenerationRepository;
import team.po.feature.devguide.repository.DevGuideRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@Service
@RequiredArgsConstructor
public class DevGuideService {
	private final DevGuideRepository devGuideRepository;
	private final DevGuideGenerationRepository devGuideGenerationRepository;
	private final GeminiClient geminiClient;
	private final DevGuidePromptBuilder promptBuilder;
	private final ProjectGroupRepository projectGroupRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final DevGuideCommandService devGuideCommandService;

	// 이벤트 핸들러에서 호출 — 트랜잭션 없이 Gemini API 호출
	public void generate(Long projectGroupId) {
		// 최초 생성 시 이미 가이드라인이 존재하는지 확인
		if (devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(projectGroupId)) {
			return;
		}

		// GENERATING 초기화. 이미 진행 중이면 중복 실행 방지
		if (!devGuideCommandService.startInitialGeneration(projectGroupId)) {
			return;
		}

		try {
			ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
				.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

			String prompt = promptBuilder.build(
				projectGroup.getProjectTitle(),
				projectGroup.getProjectDescription(),
				projectGroup.getProjectMvp()
			);

			DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);

			devGuideCommandService.create(projectGroupId, content);
		} catch (ApplicationException e) {
			devGuideCommandService.failGeneration(projectGroupId);
			throw e;
		} catch (Exception e) {
			devGuideCommandService.failGeneration(projectGroupId);
			throw e;
		}
	}

	// 재생성 API — 트랜잭션 없이 Gemini API 호출
	public DevGuideRegenerateResponse regenerate(Long projectGroupId, Long userId, String feedback) {
		validateProjectGroupMember(projectGroupId, userId);

		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		// Gemini 호출 전: 조건 검증(GENERATING 차단, 횟수 제한), 타입 결정, 상태 GENERATING 전환
		DevGuideGenerationType generationType = devGuideCommandService.startRegeneration(projectGroupId);

		try {
			String prompt = promptBuilder.build(
				projectGroup.getProjectTitle(),
				projectGroup.getProjectDescription(),
				projectGroup.getProjectMvp(),
				devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)
					.map(DevGuide::toContent)
					.orElse(null),
				feedback
			);

			DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);

			// Gemini 호출 후: 가이드 저장, 상태 COMPLETED 전환, 남은 횟수 반환
			int remainingCount = devGuideCommandService.completeRegeneration(projectGroupId, content, generationType);
			return new DevGuideRegenerateResponse(content, generationType, remainingCount);
		} catch (ApplicationException e) {
			devGuideCommandService.failGeneration(projectGroupId);
			throw e;
		} catch (Exception e) {
			devGuideCommandService.failGeneration(projectGroupId);
			throw e;
		}
	}

	public DevGuideQueryResponse getDevGuide(Long projectGroupId, Long userId) {
		validateProjectGroupMember(projectGroupId, userId);

		Optional<DevGuide> devGuide = devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(projectGroupId);
		Optional<DevGuideGeneration> generation = devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId);

		if (devGuide.isEmpty() && generation.isEmpty()) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND);
		}

		// 생성 레코드가 없는 경우(마이그레이션 등) confirmed 가이드 기준으로 COMPLETED 반환
		DevGuideStatus status = generation
			.map(DevGuideGeneration::getStatus)
			.orElse(DevGuideStatus.COMPLETED);

		DevGuideContent content = devGuide.map(DevGuide::toContent).orElse(null);

			Integer remainingCount = generation
				.map(g -> g.getMaxRegenerationCount()
						- devGuideRepository.countByProjectGroup_IdAndGenerationType(
						projectGroupId, DevGuideGenerationType.MANUAL))
				.orElse(null);

		return new DevGuideQueryResponse(content, status, remainingCount);
	}

	public void confirm(Long projectGroupId, Long userId, Long devGuideId) {
		// 프로젝트 그룹 소속 검증
		validateProjectGroupMember(projectGroupId, userId);
		// 버전 확정은 트랜잭션 안에서 수행
		devGuideCommandService.confirm(projectGroupId, devGuideId);
	}

	public DevGuideHistoryListResponse getHistories(Long projectGroupId, Long userId) {
		// 프로젝트 그룹 소속 검증
		validateProjectGroupMember(projectGroupId, userId);

		// 가이드라인 생성 중에는 버전 목록 조회 불가
		devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId)
			.filter(DevGuideGeneration::isGenerating)
			.ifPresent(generation -> {
				throw new ApplicationException(ErrorCode.DEV_GUIDE_GENERATING);
			});

		return new DevGuideHistoryListResponse(
			devGuideRepository.findAllByProjectGroup_IdAndDeletedAtIsNullOrderByVersionNoDesc(projectGroupId)
				.stream()
				.map(DevGuideHistoryResponse::from)
				.toList()
		);
	}

	public DevGuideHistoryContentResponse getHistoryContent(Long projectGroupId, Long userId, Long devGuideId) {
		// 프로젝트 그룹 소속 검증
		validateProjectGroupMember(projectGroupId, userId);

		// 개발 가이드라인 존재 여부
		DevGuide devGuide = devGuideRepository.findByIdAndProjectGroup_IdAndDeletedAtIsNull(devGuideId, projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND));

		// 해당 가이드라인 내용 리턴
		return DevGuideHistoryContentResponse.from(devGuide);
	}

	private void validateProjectGroupMember(Long projectGroupId, Long userId) {
		if (!projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(projectGroupId, userId)) {
			throw new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED);
		}
	}
}

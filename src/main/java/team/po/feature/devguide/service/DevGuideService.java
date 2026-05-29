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
		if (devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)) {
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
				feedback
			);

			DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);

			// Gemini 호출 후: 가이드 저장, 상태 COMPLETED 전환, 남은 횟수 반환
			int remainingCount = devGuideCommandService.completeRegeneration(projectGroupId, content, generationType);

			return new DevGuideRegenerateResponse(content, generationType, remainingCount);
		} catch (Exception e) {
			devGuideCommandService.failGeneration(projectGroupId);
			throw e;
		}
	}

	public DevGuideQueryResponse getDevGuide(Long projectGroupId, Long userId) {
		validateProjectGroupMember(projectGroupId, userId);

		Optional<DevGuide> devGuide = devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(projectGroupId);
		if (devGuide.isPresent()) {
			return new DevGuideQueryResponse(devGuide.get().toContent(), DevGuideStatus.COMPLETED);
		}

		DevGuideGeneration generation = devGuideGenerationRepository
			.findByProjectGroup_Id(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND));

		return new DevGuideQueryResponse(null, generation.getStatus());
	}

	private void validateProjectGroupMember(Long projectGroupId, Long userId) {
		if (!projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(projectGroupId, userId)) {
			throw new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED);
		}
	}
}
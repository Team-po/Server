package team.po.feature.devguide.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.client.GeminiClient;
import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.prompt.DevGuidePromptBuilder;
import team.po.feature.devguide.prompt.DevGuideSchema;
import team.po.feature.devguide.repository.DevGuideRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@Service
@RequiredArgsConstructor
public class DevGuideService {
	private final DevGuideRepository devGuideRepository;
	private final GeminiClient geminiClient;
	private final DevGuidePromptBuilder promptBuilder;
	private final ProjectGroupRepository projectGroupRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final DevGuideCommandService devGuideCommandService;

	// Transaction 없이 Gemini API 호출
	public void generate(Long projectGroupId) {
		// 이미 가이드라인이 존재하는 경우 API 호출하지 않고 return
		if (devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)) {
			return;
		}

		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		String prompt = promptBuilder.build(
			projectGroup.getProjectTitle(),
			projectGroup.getProjectDescription(),
			projectGroup.getProjectMvp()
		);

		DevGuideContent content = geminiClient.generateDevGuide(
			prompt,
			DevGuideSchema.RESPONSE_SCHEMA
		);

		devGuideCommandService.create(projectGroupId, content);
	}

	public DevGuideContent getDevGuide(Long projectGroupId, Long userId) {
		// 조회 가능한 유저인지 검증
		validateProjectGroupMember(projectGroupId, userId);

		DevGuide devGuide = devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND));

		return devGuide.toContent();
	}

	private void validateProjectGroupMember(Long projectGroupId, Long userId) {
		if (!projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(projectGroupId, userId)) {
			throw new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED);
		}
	}
}
